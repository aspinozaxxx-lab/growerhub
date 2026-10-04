package ru.growerhub.backend.notification.engine;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.notification.jpa.*;
import ru.growerhub.backend.user.UserFacade;

@Service
public class TelegramService {
    private final TelegramChannelRepository channels;
    private final TelegramDeliveryRepository deliveries;
    private final TelegramUpdateRepository updates;
    private final UserFacade users;
    private final JournalFacade journal;
    private final TelegramSettings settings;
    private final ObjectMapper json;
    public TelegramService(TelegramChannelRepository channels, TelegramDeliveryRepository deliveries,
            TelegramUpdateRepository updates, @Lazy UserFacade users, @Lazy JournalFacade journal,
            TelegramSettings settings, ObjectMapper json) {
        this.channels = channels; this.deliveries = deliveries; this.updates = updates; this.users = users;
        this.journal = journal; this.settings = settings; this.json = json;
    }
    public TelegramData.Status status(AuthenticatedUser user) {
        requireAccount(user);
        var c = channels.findById(user.id()).orElse(null);
        boolean pending = c != null && c.pendingChatId != null && c.linkExpiresAt != null && c.linkExpiresAt.isAfter(now());
        var profile = users.getUser(user.id());
        return new TelegramData.Status(available(), c != null && c.chatId != null, c == null ? null : c.name,
                pending ? c.pendingName : null, pending ? c.confirmation : null, c != null && c.enabled,
                c == null ? settings.defaultHour() : c.digestHour, c == null ? settings.quietFrom() : c.quietFrom,
                c == null ? settings.quietUntil() : c.quietUntil, profile.timezone(), c == null ? null : c.lastStatus);
    }
    public TelegramData.Link link(AuthenticatedUser user) {
        requireAccount(user); requireAvailable(); users.lockAccount(user.id());
        var c = channels.findById(user.id()).orElseGet(() -> defaults(user.id()));
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        c.linkHash = hash(token); c.linkExpiresAt = now().plusMinutes(settings.linkMinutes());
        c.pendingChatId = null; c.pendingName = null; c.confirmation = null;
        channels.save(c);
        return new TelegramData.Link("https://t.me/" + settings.botUsername() + "?start=" + token, c.linkExpiresAt);
    }
    public TelegramData.Status confirm(AuthenticatedUser user, String confirmation) {
        requireAccount(user); requireAvailable(); users.lockAccount(user.id());
        var c = channels.lock(user.id()).orElseThrow(() -> error("Сначала откройте бота по ссылке"));
        if (c.pendingChatId == null || c.linkExpiresAt == null || !c.linkExpiresAt.isAfter(now())
                || confirmation == null || !confirmation.equals(c.confirmation)) throw error("Ссылка привязки истекла или была заменена");
        var existing = channels.findByChatId(c.pendingChatId);
        if (existing.isPresent() && !existing.get().userId.equals(user.id())) throw error("Этот Telegram уже связан с другим аккаунтом");
        deliveries.cancel(user.id()); c.addressVersion++;
        c.chatId = c.pendingChatId; c.name = c.pendingName; c.enabled = true; clearPending(c); c.lastStatus = "connected";
        channels.save(c);
        enqueue(c, "welcome:" + c.addressVersion, "Telegram подключён к GrowerHub. Здесь будут напоминания о ручном уходе. /today — дела, /stop — отключить уведомления. Насосами этот бот не управляет.", null);
        return status(user);
    }
    public TelegramData.Status preferences(AuthenticatedUser user, TelegramData.Preferences p) {
        requireAccount(user); users.lockAccount(user.id());
        if (!hour(p.hour()) || !hour(p.quietFrom()) || !hour(p.quietUntil())) throw error("Время должно быть от 0 до 23 часов");
        var c = channels.findById(user.id()).orElseGet(() -> defaults(user.id()));
        if (p.enabled() && c.chatId == null) throw error("Сначала подключите Telegram");
        c.enabled = p.enabled(); c.digestHour = p.hour(); c.quietFrom = p.quietFrom(); c.quietUntil = p.quietUntil();
        if (!p.enabled()) deliveries.cancel(user.id());
        channels.save(c); return status(user);
    }
    public void disconnect(AuthenticatedUser user) {
        requireAccount(user); users.lockAccount(user.id());
        channels.findById(user.id()).ifPresent(c -> { deliveries.cancel(user.id()); c.enabled = false;
            c.chatId = null; c.name = null; c.addressVersion++; c.lastStatus = "disconnected"; clearPending(c); channels.save(c); });
    }
    public void test(AuthenticatedUser user) {
        requireAccount(user); requireAvailable(); users.lockAccount(user.id());
        var c = channels.findById(user.id()).orElseThrow(() -> error("Сначала подключите Telegram"));
        if (!c.enabled || c.chatId == null) throw error("Включите уведомления");
        if (c.lastTestAt != null && c.lastTestAt.plusSeconds(settings.retrySeconds()).isAfter(now())) throw error("Пробное сообщение уже отправлено; подождите немного");
        c.lastTestAt = now(); channels.save(c);
        enqueue(c, "test:" + UUID.randomUUID(), "Пробное сообщение GrowerHub 🌱\nВсё работает. Напоминания придут, когда появятся назначенные дела.", null);
    }
    public String receive(TelegramData.Update update) {
        if (!available() || !update.privateChat()) return null;
        String text = Objects.toString(update.text(), "");
        boolean start = text.startsWith("/start ");
        var candidate = start ? channels.findByLinkHash(hash(text.substring(7).trim())) : channels.findByChatId(update.chatId());
        if (candidate.isEmpty()) return text.startsWith("/start") || text.equals("/help")
                ? "Привет! Я помогаю вести дневник растений 🌱\nПодключите меня через настройки GrowerHub: " + settings.siteUrl() + "/app/settings/notifications/\nПосле запуска по ссылке вернитесь на сайт и подтвердите свой Telegram." : null;
        var user = users.getAuthUser(candidate.get().userId);
        if (user == null || !user.active()) return null;
        users.lockAccount(user.id());
        var c = channels.lock(user.id()).orElseThrow();
        if (updates.existsById(update.updateId())) return null;
        var inbound = new TelegramUpdateEntity(); inbound.id = update.updateId(); inbound.receivedAt = now(); updates.save(inbound);
        if (start) {
            if (c.linkHash == null || c.linkExpiresAt == null || !c.linkExpiresAt.isAfter(now())) return null;
            if (channels.findByChatId(update.chatId()).filter(other -> !other.userId.equals(c.userId)).isPresent()
                    || channels.findByPendingChatId(update.chatId()).filter(other -> !other.userId.equals(c.userId)).isPresent()) return null;
            c.pendingChatId = update.chatId(); c.pendingName = update.name(); c.confirmation = UUID.randomUUID().toString();
            c.linkHash = null; channels.save(c);
            // Otvet do podtverzhdeniya dopuskaet tolko instrukciyu, bez dannyh akkaunta.
            enqueuePending(c, update.updateId());
            return null;
        }
        if (!Objects.equals(c.chatId, update.chatId())) return null;
        if (text.equals("/stop")) { c.enabled = false; c.lastStatus = "stopped"; deliveries.cancel(c.userId); channels.save(c); return "Напоминания отключены. Снова включить их можно в настройках GrowerHub."; }
        if (update.callbackData() != null && c.enabled) {
            String[] parts = update.callbackData().split(":");
            if (parts.length == 3 && Set.of("done", "snooze", "skip").contains(parts[0])) {
                try {
                    int reminderId = Integer.parseInt(parts[1]);
                    var reminder = journal.careReminders(new AuthenticatedUser(c.userId, user.role())).stream()
                            .filter(r -> r.id() == reminderId && r.occurrenceKey().equals(parts[2])).findFirst();
                    if (reminder.isPresent()) {
                        journal.actCareReminder(reminderId, new AuthenticatedUser(c.userId, user.role()), parts[0],
                                new CareData.ReminderAction(parts[2], now().atOffset(ZoneOffset.UTC).atZoneSameInstant(zone(c.userId)).plusDays(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
                        enqueue(c, "callback:" + update.updateId(), parts[0].equals("done") ? "Готово, уход записан 🌱" : "Напоминание перенесено или пропущено.", null);
                    }
                } catch (NumberFormatException ignored) { }
            }
        } else if (text.equals("/today") && c.enabled) {
            digest(c, "request:" + update.updateId(), true);
        } else if (c.enabled) {
            enqueue(c, "help:" + update.updateId(), "Дневник растений — " + settings.siteUrl() + "/app/plants/\n/today — назначенные дела\n/stop — отключить напоминания\nФото и заметки пока добавляйте в дневнике на сайте.", null);
        }
        return null;
    }
    public void prepare() {
        if (!available()) return;
        deliveries.expireLeases(now());
        for (var snapshot : channels.findByEnabledTrue()) {
            var user = users.getAuthUser(snapshot.userId);
            if (user == null || !user.active()) continue;
            users.lockAccount(user.id());
            var c = channels.lock(user.id()).orElseThrow();
            if (!c.enabled || c.chatId == null) continue;
            var local = now().atOffset(ZoneOffset.UTC).atZoneSameInstant(zone(c.userId));
            LocalDate digestDate = digestDate(local, c.digestHour, c.quietFrom, c.quietUntil);
            if (digestDate == null) continue;
            digest(c, "daily:" + c.addressVersion + ":" + digestDate, false);
        }
        updates.deleteByReceivedAtBefore(now().minusDays(settings.retentionDays()));
        deliveries.purge(now().minusDays(settings.retentionDays()));
    }
    public List<TelegramData.Delivery> claim() {
        if (!available()) return List.of();
        List<TelegramData.Delivery> result = new ArrayList<>();
        for (var d : deliveries.ready(now(), PageRequest.of(0, settings.batchSize()))) {
            var c = channels.findById(d.userId).orElse(null);
            boolean pending = d.dedupeKey.startsWith(d.userId + ":pending:");
            boolean valid = c != null && (pending ? Objects.equals(c.pendingChatId, d.chatId)
                    : c.enabled && c.addressVersion == d.addressVersion && Objects.equals(c.chatId, d.chatId));
            if (!valid || !d.expiresAt.isAfter(now()) || !currentTasks(d)) { d.status = "cancelled"; continue; }
            if (d.dedupeKey.contains(":daily:") && quiet(now().atOffset(ZoneOffset.UTC).atZoneSameInstant(zone(d.userId)).getHour(), c.quietFrom, c.quietUntil)) continue;
            d.status = "sending"; d.leasedUntil = now().plusSeconds(settings.leaseSeconds());
            d.leaseKey = UUID.randomUUID().toString(); d.attempts++;
            result.add(new TelegramData.Delivery(d.id, d.chatId, d.text, d.keyboard, d.leaseKey));
        }
        return result;
    }
    public void finish(TelegramData.Delivery sent, TelegramData.Result result) {
        var d = deliveries.findById(sent.id()).orElse(null);
        if (d == null || !"sending".equals(d.status) || !Objects.equals(d.leaseKey, sent.leaseKey())) return;
        var c = channels.findById(d.userId).orElse(null);
        d.status = result.status();
        if ("retry".equals(result.status()) && d.attempts < settings.maxAttempts()) {
            d.status = "queued"; d.availableAt = now().plusSeconds(Math.max(settings.retrySeconds(), Objects.requireNonNullElse(result.retryAfter(), 0)));
        }
        if (c != null) {
            c.lastStatus = d.status;
            if ("blocked".equals(result.status())) { c.enabled = false; deliveries.cancel(c.userId); }
        }
    }
    private boolean currentTasks(TelegramDeliveryEntity delivery) {
        if (delivery.keyboard == null) return true;
        var tasks = journal.careReminders(new AuthenticatedUser(delivery.userId, "user"));
        var keys = tasks.stream().map(r -> "done:" + r.id() + ":" + r.occurrenceKey()).collect(java.util.stream.Collectors.toSet());
        try {
            for (var row : json.readTree(delivery.keyboard).path("inline_keyboard")) {
                for (var button : row) {
                    String callback = button.path("callback_data").asText("");
                    if (callback.startsWith("done:") && !keys.contains(callback)) return false;
                }
            }
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { return false; }
    }
    public static LocalDate digestDate(ZonedDateTime local, int hour, int from, int until) {
        if (quiet(local.getHour(), from, until)) return null;
        int sendHour = quiet(hour, from, until) ? until : hour;
        if (local.getHour() < sendHour) return null;
        return from > until && hour >= from ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }
    private void digest(TelegramChannelEntity c, String key, boolean explicit) {
        String fullKey = c.userId + ":" + key;
        if (deliveries.existsByDedupeKey(fullKey)) return;
        var due = journal.careReminders(new AuthenticatedUser(c.userId, "user")).stream()
                .filter(r -> !r.dueAt().isAfter(now())).toList();
        var tasks = due.stream().limit(settings.digestSize()).toList();
        if (tasks.isEmpty() && !explicit) return;
        StringBuilder message = new StringBuilder(tasks.isEmpty() ? "На сегодня назначенных дел нет 🌱" : "Пора заглянуть к растениям 🌱\n");
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        int i = 0;
        for (var r : tasks) {
            i++; message.append(i).append(". ").append(r.plantName()).append(" — ").append(r.title()).append('\n');
            if (r.action().equals("watering")) message.append("Сначала проверьте, нужен ли полив.\n");
            keyboard.add(List.of(Map.of("text", i + (r.action().equals("watering") ? " · Полито" : " · Выполнено"), "callback_data", "done:" + r.id() + ":" + r.occurrenceKey()),
                    Map.of("text", "Завтра", "callback_data", "snooze:" + r.id() + ":" + r.occurrenceKey())));
        }
        if (due.size() > tasks.size()) message.append("Ещё ").append(due.size() - tasks.size()).append(" дел — в дневнике.\n");
        keyboard.add(List.of(Map.of("text", "Открыть дневник", "url", settings.siteUrl() + "/app/plants/")));
        try { enqueue(c, key, message.toString(), json.writeValueAsString(Map.of("inline_keyboard", keyboard))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Telegram keyboard serialization failed"); }
    }
    private void enqueuePending(TelegramChannelEntity c, long updateId) {
        var d = delivery(c, "pending:" + updateId, "Остался один шаг: вернитесь в GrowerHub и подтвердите свой Telegram-профиль в настройках уведомлений.", null);
        d.chatId = c.pendingChatId; deliveries.save(d);
    }
    private void enqueue(TelegramChannelEntity c, String key, String text, String keyboard) { deliveries.save(delivery(c, key, text, keyboard)); }
    private TelegramDeliveryEntity delivery(TelegramChannelEntity c, String key, String text, String keyboard) {
        var d = new TelegramDeliveryEntity(); d.userId = c.userId; d.chatId = c.chatId == null ? 0 : c.chatId;
        d.addressVersion = c.addressVersion; d.dedupeKey = c.userId + ":" + key; d.text = text; d.keyboard = keyboard;
        d.status = "queued"; d.availableAt = now(); d.expiresAt = now().plusHours(settings.deliveryHours()); return d;
    }
    private TelegramChannelEntity defaults(Integer id) { var c = new TelegramChannelEntity(); c.userId = id;
        c.digestHour = settings.defaultHour(); c.quietFrom = settings.quietFrom(); c.quietUntil = settings.quietUntil(); return c; }
    private void clearPending(TelegramChannelEntity c) { c.linkHash = null; c.linkExpiresAt = null; c.pendingChatId = null; c.pendingName = null; c.confirmation = null; }
    public static boolean quiet(int hour, int from, int until) { return from != until && (from < until ? hour >= from && hour < until : hour >= from || hour < until); }
    private boolean hour(int h) { return h >= 0 && h < 24; }
    private ZoneId zone(Integer id) { return ZoneId.of(users.getUser(id).timezone()); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private boolean available() { return settings.enabled() && settings.botToken() != null && !settings.botToken().isBlank(); }
    private void requireAvailable() { if (!available()) throw error("Подключение Telegram временно недоступно"); }
    private void requireAccount(AuthenticatedUser user) { if (user == null || user.isDemo() || users.getAuthUser(user.id()) == null) throw new DomainException("forbidden", "Telegram доступен только в личном аккаунте"); }
    private DomainException error(String message) { return new DomainException("conflict", message); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
}
