package ru.growerhub.backend.shop.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.ShopSettings;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.shop.contract.ShopData;
import ru.growerhub.backend.shop.contract.ShopException;
import ru.growerhub.backend.shop.jpa.*;

@Service
public class ShopService {
    private final ShopRequestRepository requests;
    private final ShopNotificationRepository notifications;
    private final ShopSubmissionGuardRepository guard;
    private final ShopData.Definition definition;
    private final ShopSettings settings;
    private final TelegramSettings telegram;
    private final ObjectMapper json;
    private final Clock clock;

    public ShopService(ShopRequestRepository requests, ShopNotificationRepository notifications,
            ShopSubmissionGuardRepository guard, ShopData.Definition definition, ShopSettings settings,
            TelegramSettings telegram, ObjectMapper json, Clock clock) {
        this.requests = requests; this.notifications = notifications; this.guard = guard;
        this.definition = definition; this.settings = settings; this.telegram = telegram;
        this.json = json; this.clock = clock;
    }

    public ShopData.Catalog catalog() {
        return new ShopData.Catalog(definition.version(), definition.currency(), definition.offers(), accepting());
    }

    public ShopData.Receipt submit(ShopData.Submission raw, String clientAddress) {
        var input = normalize(raw);
        String fingerprint = hash(write(input));
        // Korotkaya blokirovka serializuet proverku UUID i limitov mezhdu instansami; seti zdes net.
        guard.lock().orElseThrow(() -> new ShopException("SHOP_UNAVAILABLE", "Приём заявок временно недоступен. Попробуйте позже."));
        var previous = requests.findByIdempotencyKey(input.idempotencyKey().toString());
        if (previous.isPresent()) {
            if (!previous.get().fingerprint.equals(fingerprint)) throw new ShopException("IDEMPOTENCY_CONFLICT", "Этот номер отправки уже использован для другой заявки.");
            return receipt(previous.get());
        }
        if (!accepting()) throw new ShopException("SHOP_UNAVAILABLE", "Приём заявок временно недоступен. Попробуйте позже.");
        if (!definition.version().equals(input.catalogVersion())) throw new ShopException("CATALOG_CHANGED", "Комплекты обновились. Проверьте состав и стоимость перед отправкой.");
        var now = now();
        String addressHash = rateHash("address:" + Objects.toString(clientAddress, "unknown"));
        String phoneHash = rateHash("phone:" + input.customer().phone().replaceAll("\\D", ""));
        var since = now.minusMinutes(settings.rateWindowMinutes());
        if (requests.countByAddressHashAndCreatedAtAfter(addressHash, since) >= settings.maxRequestsPerAddress()
                || requests.countByPhoneHashAndCreatedAtAfter(phoneHash, since) >= settings.maxRequestsPerPhone())
            throw new ShopException("RATE_LIMITED", "Вы уже отправили несколько заявок. Пожалуйста, попробуйте позже.", settings.rateWindowMinutes() * 60L);
        var items = snapshot(input.items());
        long total = items.stream().mapToLong(item -> Math.multiplyExact(item.unitPriceMinor(), item.quantity())).reduce(0L, Math::addExact);
        var request = new ShopRequestEntity();
        request.idempotencyKey = input.idempotencyKey().toString(); request.fingerprint = fingerprint;
        request.kind = input.kind(); request.status = ShopData.Status.NEW; request.catalogVersion = definition.version();
        request.currency = definition.currency(); request.totalMinor = total; request.itemsJson = write(items);
        request.customerName = input.customer().name(); request.customerPhone = input.customer().phone();
        request.customerTelegram = input.customer().telegram(); request.comment = input.comment();
        if (input.pickup() != null) { request.pickupCity = input.pickup().city(); request.pickupCode = input.pickup().code(); request.pickupAddress = input.pickup().address(); }
        request.addressHash = addressHash; request.phoneHash = phoneHash; request.consentAt = now;
        request.createdAt = now; request.updatedAt = now; requests.saveAndFlush(request);
        var notification = new ShopNotificationEntity(); notification.requestId = request.id;
        notification.status = notificationAvailable() ? "queued" : "blocked";
        notification.lastError = notificationAvailable() ? null : "NOT_CONFIGURED";
        notification.availableAt = now; notification.updatedAt = now; notifications.save(notification);
        return receipt(request);
    }

    public ShopData.Requests list(AuthenticatedUser user, ShopData.Status status, int page, int size) {
        requireAdmin(user);
        if (page < 0 || size < 1 || size > settings.maxPageSize()) throw invalid("Некорректный номер или размер страницы.");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable);
        return new ShopData.Requests(result.getContent().stream().map(this::details).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public ShopData.Request get(AuthenticatedUser user, Long id) {
        requireAdmin(user); return details(requests.findById(id).orElseThrow(this::notFound));
    }

    public ShopData.Request update(AuthenticatedUser user, Long id, ShopData.Status status) {
        requireAdmin(user);
        if (status == null) throw invalid("Выберите состояние заявки.");
        var request = requests.lock(id).orElseThrow(this::notFound);
        request.status = status; request.updatedAt = now(); return details(request);
    }

    public ShopData.Request retry(AuthenticatedUser user, Long id) {
        requireAdmin(user);
        var request = requests.findById(id).orElseThrow(this::notFound);
        var notification = notifications.lockByRequestId(id).orElseThrow(this::notFound);
        if (!Set.of("failed", "uncertain", "blocked").contains(notification.status)) throw new ShopException("NOTIFICATION_NOT_RETRYABLE", "Сообщение уже отправлено или ожидает отправки.");
        if (!notificationAvailable()) throw new ShopException("SHOP_UNAVAILABLE", "Сначала настройте Telegram для заявок.");
        notification.status = "queued"; notification.lastError = null; notification.retryAttempts = 0;
        notification.availableAt = now(); notification.updatedAt = now(); notification.leaseKey = null;
        notification.leasedUntil = null; return details(request);
    }

    public List<TelegramData.Delivery> claim() {
        var now = now(); notifications.expireLeases(now);
        if (!notificationAvailable()) return List.of();
        var batch = notifications.ready(now, PageRequest.of(0, settings.notificationBatchSize()));
        var result = new ArrayList<TelegramData.Delivery>();
        for (var notification : batch) {
            var request = requests.findById(notification.requestId).orElseThrow(this::notFound);
            notification.status = "sending"; notification.attempts++; notification.retryAttempts++;
            notification.chatId = Long.parseLong(settings.telegramChatId());
            notification.leaseKey = UUID.randomUUID().toString(); notification.leasedUntil = now.plusSeconds(settings.notificationLeaseSeconds());
            notification.updatedAt = now; notification.lastError = null;
            String text = (request.kind == ShopData.Kind.ORDER ? "Новый заказ GrowerHub " : "Нужна помощь с комплектом GrowerHub ") + number(request)
                    + (request.kind == ShopData.Kind.ORDER ? "\nКомплекты: " + String.format(Locale.ROOT, "%.2f", request.totalMinor / 100.0) + " ₽. Доставка отдельно." : "")
                    + "\n" + settings.adminUrl() + "?request=" + request.id;
            result.add(new TelegramData.Delivery(notification.id, notification.chatId, text, null, notification.leaseKey));
        }
        return result;
    }

    public void finish(TelegramData.Delivery delivery, TelegramData.Result result) {
        var notification = notifications.lock(delivery.id()).orElse(null);
        if (notification == null || !"sending".equals(notification.status) || !Objects.equals(notification.leaseKey, delivery.leaseKey())) return;
        String status = result == null ? "uncertain" : result.status();
        notification.updatedAt = now(); notification.leasedUntil = null;
        switch (status) {
            case "accepted" -> { notification.status = "accepted"; notification.lastError = null; }
            case "retry" -> {
                notification.status = notification.retryAttempts < settings.notificationMaxAttempts() ? "queued" : "failed";
                notification.lastError = "RATE_LIMITED";
                notification.availableAt = now().plusSeconds(Math.max(settings.notificationRetrySeconds(), Objects.requireNonNullElse(result.retryAfter(), 0)));
            }
            case "blocked" -> { notification.status = "blocked"; notification.lastError = "CHAT_BLOCKED"; }
            case "failed" -> { notification.status = "failed"; notification.lastError = "PROVIDER_REJECTED"; }
            default -> { notification.status = "uncertain"; notification.lastError = "DELIVERY_UNCERTAIN"; }
        }
    }

    private ShopData.Submission normalize(ShopData.Submission input) {
        if (input == null || input.kind() == null || input.idempotencyKey() == null || !Boolean.TRUE.equals(input.consent())
                || input.customer() == null || (input.website() != null && !input.website().isBlank())) throw invalid("Проверьте обязательные поля и согласие на обработку данных.");
        String name = field(input.customer().name(), settings.maxNameLength(), true, false);
        String phone = field(input.customer().phone(), settings.maxPhoneLength(), true, false);
        if (!phone.matches("[+0-9 ()-]+")) throw invalid("Укажите номер телефона с кодом страны.");
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < settings.minPhoneDigits() || digits.length() > settings.maxPhoneDigits()) throw invalid("Проверьте номер телефона.");
        phone = (phone.startsWith("+") ? "+" : "") + digits;
        var customer = new ShopData.Customer(name, phone, field(input.customer().telegram(), settings.maxTelegramLength(), false, false));
        var items = input.items() == null ? List.<ShopData.Item>of() : input.items();
        if (items.size() > settings.maxItems() || (input.kind() == ShopData.Kind.ORDER && items.isEmpty())) throw invalid("Выберите комплект для заказа.");
        if (input.kind() == ShopData.Kind.CONSULTATION && (!items.isEmpty() || input.pickup() != null)) throw invalid("Для консультации не требуется корзина или пункт выдачи.");
        var identifiers = new HashSet<String>();
        for (var item : items) {
            if (item == null || item.offerId() == null || item.offerId().length() > settings.maxCatalogVersionLength()
                    || !identifiers.add(item.offerId()) || item.quantity() < 1 || item.quantity() > settings.maxQuantity())
                throw invalid("Проверьте количество комплектов в корзине.");
        }
        ShopData.Pickup pickup = null;
        if (input.kind() == ShopData.Kind.ORDER) {
            if (input.pickup() == null) throw invalid("Укажите город, номер и адрес пункта выдачи СДЭК.");
            pickup = new ShopData.Pickup(field(input.pickup().city(), settings.maxCityLength(), true, false),
                    field(input.pickup().code(), settings.maxPickupCodeLength(), true, false),
                    field(input.pickup().address(), settings.maxPickupAddressLength(), true, false));
        }
        return new ShopData.Submission(input.kind(), input.idempotencyKey(), field(input.catalogVersion(), settings.maxCatalogVersionLength(), true, false),
                items.stream().sorted(Comparator.comparing(ShopData.Item::offerId)).toList(), customer, pickup,
                field(input.comment(), settings.maxCommentLength(), false, true), true, "");
    }

    private String field(String value, int maxLength, boolean required, boolean multiline) {
        String result = value == null ? "" : value.trim().replace("\r\n", "\n");
        if ((required && result.isEmpty()) || result.length() > maxLength
                || result.chars().anyMatch(c -> Character.isISOControl(c) && !(multiline && c == '\n')))
            throw invalid("Проверьте заполнение и длину полей формы.");
        return result.isEmpty() ? null : result;
    }

    private List<ShopData.SnapshotItem> snapshot(List<ShopData.Item> items) {
        return items.stream().map(item -> {
            var offer = definition.offers().stream().filter(o -> o.id().equals(item.offerId())).findFirst()
                    .orElseThrow(() -> invalid("Один из выбранных комплектов недоступен."));
            return new ShopData.SnapshotItem(offer.id(), item.quantity(), offer.priceMinor(), offer.hubModel(),
                    offer.hubEquipmentId(), offer.socketEquipmentId(), offer.socketCount(), offer.verification(), offer.components());
        }).toList();
    }

    private ShopData.Request details(ShopRequestEntity request) {
        var notification = notifications.findByRequestId(request.id).orElseThrow(this::notFound);
        List<ShopData.SnapshotItem> items;
        try { items = json.readValue(request.itemsJson, new TypeReference<>() { }); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Invalid shop snapshot"); }
        return new ShopData.Request(request.id, number(request), request.kind, request.status, request.createdAt,
                request.updatedAt, request.totalMinor, request.currency, request.catalogVersion, items,
                new ShopData.Customer(request.customerName, request.customerPhone, request.customerTelegram),
                request.pickupCity == null ? null : new ShopData.Pickup(request.pickupCity, request.pickupCode, request.pickupAddress),
                request.comment, new ShopData.Notification(notification.status, notification.attempts, notification.lastError, notification.updatedAt));
    }

    private ShopData.Receipt receipt(ShopRequestEntity request) {
        return new ShopData.Receipt(number(request), request.totalMinor, request.currency, request.createdAt, ShopData.Status.NEW);
    }
    private String number(ShopRequestEntity request) { return "GH-" + String.format(Locale.ROOT, "%06d", request.id); }
    private boolean accepting() { return settings.acceptingRequests() && settings.rateSecret() != null && !settings.rateSecret().isBlank(); }
    private boolean notificationAvailable() {
        return telegram.enabled() && telegram.botToken() != null && !telegram.botToken().isBlank()
                && settings.telegramChatId() != null && settings.telegramChatId().matches("-?[1-9][0-9]{0,17}");
    }
    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) throw new ShopException("FORBIDDEN", "Доступ разрешён только администратору.");
    }
    private ShopException invalid(String message) { return new ShopException("INVALID_REQUEST", message); }
    private ShopException notFound() { return new ShopException("NOT_FOUND", "Заявка не найдена."); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Shop serialization failed"); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String rateHash(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(settings.rateSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Shop rate protection unavailable"); }
    }
}
