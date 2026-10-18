package ru.growerhub.backend.advisor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.advisor.jpa.*;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.user.UserFacade;

@Service
public class AssistantRequests {
    private final AssistantRequestRepository repository;
    private final UserFacade users;
    private final DemoFacade demo;
    private final NotificationFacade notifications;
    private final AssistantSettings settings;
    private final ObjectMapper json;
    private final Clock clock;
    public AssistantRequests(AssistantRequestRepository repository, UserFacade users, DemoFacade demo,
            NotificationFacade notifications, AssistantSettings settings, ObjectMapper json, Clock clock) {
        this.repository = repository; this.users = users; this.demo = demo; this.notifications = notifications;
        this.settings = settings; this.json = json; this.clock = clock;
    }
    public JsonNode validate(JsonNode payload, AuthenticatedUser user) {
        account(user);
        var fields = new LinkedHashMap<String, String>();
        for (var key : List.of("title", "description", "benefit")) {
            var value = payload.path(key);
            int limit = "title".equals(key) ? 120 : "description".equals(key) ? 2000 : 1000;
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > limit)
                throw bad("Заполните краткое название, описание задачи и ожидаемую пользу");
            fields.put(key, value.asText().strip());
        }
        if (payload.size() != fields.size()) throw bad("В заявку входят только название, задача и ожидаемая польза");
        return json.valueToTree(fields);
    }
    public FeatureRequest create(UUID id, JsonNode payload, AuthenticatedUser user) {
        int account = account(user); users.lockAccount(account);
        var old = repository.findById(id).orElse(null);
        if (old != null) {
            if (!old.userId.equals(account)) throw new DomainException("not_found", "Заявка не найдена");
            return view(old);
        }
        var clean = validate(payload, user);
        if (repository.countByUserIdAndCreatedAtAfter(account, now().minusDays(1)) >= settings.requestsPerDay())
            throw bad("За сутки уже отправлено несколько заявок. Объедините идеи или вернитесь завтра");
        var r = new AssistantRequestEntity(); r.id = id; r.userId = account;
        r.title = clean.get("title").asText(); r.description = clean.get("description").asText(); r.benefit = clean.get("benefit").asText();
        r.status = "new"; r.note = ""; r.revision = 1; r.createdAt = now(); r.updatedAt = r.createdAt;
        return view(repository.save(r));
    }
    public List<FeatureRequest> list(AuthenticatedUser user, boolean admin, int page) {
        if (page < 0) throw bad("Неверная страница");
        if (admin) requireAdmin(user);
        var pageable = PageRequest.of(page, settings.limits().queueSize());
        return (admin ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(account(user), pageable)).stream().map(this::view).toList();
    }
    public FeatureRequest decide(UUID id, FeatureDecision decision, AuthenticatedUser user) {
        requireAdmin(user);
        if (decision == null || !Set.of("new", "planned", "declined", "done").contains(Objects.toString(decision.status(), ""))
                || decision.note() == null || decision.note().length() > 1000) throw bad("Проверьте решение и комментарий");
        var r = repository.lock(id).orElseThrow(() -> new DomainException("not_found", "Заявка не найдена"));
        if (r.revision != decision.revision()) throw new DomainException("conflict", "Заявка уже изменена. Обновите список");
        r.status = decision.status(); r.note = decision.note().strip(); r.revision++; r.updatedAt = now();
        return view(repository.save(r));
    }
    public void notifyOwner() {
        var owner = settings.ownerUserId() == null ? null : users.getAuthUser(settings.ownerUserId());
        if (owner == null || !owner.active() || !"admin".equals(owner.role())) return;
        var principal = new AuthenticatedUser(owner.id(), "admin");
        for (var r : repository.findByNotificationQueuedFalseOrderByCreatedAtAsc(PageRequest.of(0, settings.limits().queueSize()))) {
            if (notifications.enqueueMessage(principal, "assistant-request:" + r.id,
                    "Запрос на доработку GrowerHub: " + r.title + "\nПользователь №" + r.userId
                    + "\n\n" + r.description + "\n\nЗачем: " + r.benefit + "\n\nРассмотреть: "
                    + settings.siteUrl() + "/app/admin/assistant/", null)) {
                r.notificationQueued = true; repository.save(r);
            }
        }
    }
    private int account(AuthenticatedUser user) {
        Integer id = user.isDemo() ? demo.assistantIdentity(user).accountId() : user.id();
        if (id == null) throw new DomainException("forbidden", "Войдите в GrowerHub, чтобы отправить запрос команде");
        var account = users.getAuthUser(id);
        if (account == null || !account.active()) throw new DomainException("forbidden", "Аккаунт недоступен");
        return id;
    }
    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || user.isDemo()) throw new DomainException("forbidden", "Требуются права администратора");
        var account = users.getAuthUser(user.id());
        if (account == null || !account.active() || !"admin".equals(account.role())) throw new DomainException("forbidden", "Требуются права администратора");
    }
    private FeatureRequest view(AssistantRequestEntity r) {
        return new FeatureRequest(r.id, r.userId, r.title, r.description, r.benefit, r.status, r.note,
                r.revision, r.notificationQueued, r.createdAt, r.updatedAt);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private DomainException bad(String text) { return new DomainException("unprocessable", text); }
}
