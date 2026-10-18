package ru.growerhub.backend.advisor.engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.advisor.jpa.*;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.user.UserFacade;
@Service
public class AssistantChannels {
    private final AssistantService assistant;
    private final AssistantTools tools;
    private final AssistantPreferencesRepository preferences;
    private final AssistantConversationRepository conversations;
    private final AssistantJobRepository jobs;
    private final AssistantGuardRepository guards;
    private final AssistantProposalRepository proposals;
    private final NotificationFacade notifications;
    private final UserFacade users;
    private final AssistantSettings settings;
    private final Clock clock;
    private final ObjectMapper json;
    public AssistantChannels(AssistantService assistant, AssistantTools tools, AssistantPreferencesRepository preferences,
            AssistantConversationRepository conversations, AssistantJobRepository jobs, AssistantGuardRepository guards,
            NotificationFacade notifications, UserFacade users, AssistantSettings settings, Clock clock, ObjectMapper json, AssistantProposalRepository proposals) {
        this.assistant = assistant; this.tools = tools; this.preferences = preferences; this.conversations = conversations;
        this.jobs = jobs; this.guards = guards; this.notifications = notifications; this.users = users; this.settings = settings; this.clock = clock; this.json = json;
        this.proposals = proposals;
    }
    public Preferences save(Preferences value, AuthenticatedUser user) {
        if (user.isDemo()) throw new DomainException("forbidden", "Telegram и отчёты доступны в основном аккаунте");
        users.lockAccount(user.id());
        if (value == null || value.report() == null || !Set.of("off", "daily", "weekly").contains(value.report()) || value.hour() < 0 || value.hour() > 23 || value.weekday() < 1 || value.weekday() > 7)
            throw new DomainException("unprocessable", "Проверьте время и период отчёта");
        UUID id = null;
        if (value.conversationId() != null && !value.conversationId().isBlank()) {
            try { id = UUID.fromString(value.conversationId()); } catch (IllegalArgumentException ex) { throw new DomainException("unprocessable", "Неверный диалог"); }
            if (conversations.findByIdAndUserId(id, user.id()).isEmpty()) throw new DomainException("not_found", "Диалог не найден");
        }
        notifications.configureAssistant(user, value.telegram());
        var row = preferences.findById(user.id()).orElseGet(() -> { var p = new AssistantPreferencesEntity(); p.userId = user.id(); return p; });
        row.telegram = value.telegram(); row.report = value.report(); row.hour = value.hour(); row.weekday = value.weekday(); row.conversationId = id; row.updatedAt = now(); preferences.save(row);
        return value;
    }

    public void notifyCodexUpdate() {
        var user = principal(settings.ownerUserId());
        if (user == null || !tools.adminRead(user)) return;
        var state = assistant.runtimeStatus(user);
        var guard = guards.lock(1).orElseThrow();
        if (state.error() == null && state.updateAvailable() && !state.latest().equals(guard.codexNotifiedVersion)
                && notifications.enqueueMessage(user, "codex-release:" + state.latest(),
                "Для личного помощника GrowerHub вышел Codex " + state.latest() + ". Сейчас установлен " + state.installed()
                + ". Обновление не устанавливается автоматически. Проверить: " + settings.siteUrl() + "/app/admin/assistant/", null)) {
            guard.codexNotifiedVersion = state.latest(); guards.save(guard);
        }
    }
    public Incoming begin(TelegramData.Inbox item) {
        var user = principal(item.userId());
        if (user == null || !notifications.assistantAllowed(user)) throw new DomainException("forbidden", "Чат с помощником отключён");
        var p = preferences.findById(user.id()).orElse(null);
        if (p == null || !p.telegram) throw new DomainException("forbidden", "Включите помощника в приложении");
        if (item.callbackData() != null) {
            var parts = item.callbackData().split(":");
            if (parts.length != 3 || !parts[1].matches("[0-9a-f]{32}")) throw new DomainException("unprocessable", "Кнопка устарела");
            String value = parts[1]; UUID id = UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20));
            var proposal = assistant.confirm(id, new Confirm(parts[2]), user, "ghr".equals(parts[0]));
            notifications.enqueueMessage(user, "assistant-confirm:" + item.id(), switch (proposal.status()) {
                case "confirmed", "applying" -> "Подтверждение принято. Результат появится в диалоге GrowerHub.";
                case "applied" -> "Это изменение уже применено."; case "rejected" -> "Предложение отклонено."; default -> "Предложение устарело. Попросите помощника проверить настройки ещё раз.";
            }, null);
            return null;
        }
        var chat = conversation(p, user);
        PhotoPermit photo = item.photoId() == null ? null : assistant.preparePhoto(chat.id(), user, new Origin(null, null, "telegram"));
        return new Incoming(item, user, chat.id(), photo);
    }
    public void complete(Incoming work, UUID photo) {
        if (!notifications.assistantAllowed(work.user())) throw new DomainException("forbidden", "Чат с помощником отключён");
        String text = Objects.toString(work.item().text(), "");
        if ("/report".equals(text)) text = "Покажи состояние фермы, что требует внимания и что работает нормально.";
        assistant.send(work.conversation(), new Send(UUID.nameUUIDFromBytes(("telegram:" + work.item().id()).getBytes(StandardCharsets.UTF_8)), text, photo == null ? List.of() : List.of(photo)), work.user(), new Origin(null, null, "telegram"));
    }
    public void reject(TelegramData.Inbox item, String message) {
        var user = principal(item.userId());
        if (user != null) notifications.enqueueMessage(user, "assistant-error:" + item.id(), message, null);
    }
    public List<ReportDue> due() {
        if (!settings.enabled()) return List.of();
        var result = new ArrayList<ReportDue>();
        for (var p : preferences.findByReportNot("off")) {
            var user = principal(p.userId); if (user == null) continue;
            var local = clock.instant().atZone(ZoneId.of(users.getTimezone(p.userId)));
            if (local.getHour() != p.hour || "weekly".equals(p.report) && local.getDayOfWeek().getValue() != p.weekday) continue;
            String key = p.report + ":" + local.toLocalDate(); if (!key.equals(p.lastReportKey)) result.add(new ReportDue(p.userId, key));
        }
        return result;
    }
    public void report(ReportDue due) {
        var user = principal(due.userId()); if (user == null) return;
        users.lockAccount(user.id()); var p = preferences.findById(user.id()).orElse(null);
        if (p == null || "off".equals(p.report) || due.key().equals(p.lastReportKey)) return;
        var chat = conversation(p, user); String summary = plainReport(user);
        p.lastReportKey = due.key(); preferences.save(p);
        UUID key = UUID.nameUUIDFromBytes(("report:" + user.id() + ":" + due.key()).getBytes(StandardCharsets.UTF_8));
        try {
            var message = assistant.send(chat.id(), new Send(key, "Сводка по расчёту сервера:\n" + summary + "\nКоротко объясни, что требует внимания. Ничего не меняй.", List.of()), user, new Origin(null, null, "report"));
            var job = jobs.findById(message.id()).orElseThrow(); job.reportSummary = summary; jobs.save(job);
        } catch (ru.growerhub.backend.advisor.contract.AssistantLimitException ex) {
            var row = new AssistantJobEntity(); row.id = UUID.randomUUID(); row.conversationId = chat.id(); row.userId = user.id(); row.clientKey = key;
            row.status = "completed"; row.channel = "report"; row.provider = "summary"; row.model = "backend"; row.inputText = "Сводка фермы"; row.outputText = summary;
            row.attachmentsJson = "[]"; row.toolResultsJson = "{}"; row.createdAt = now(); row.finishedAt = now(); row.reportSummary = summary; jobs.save(row);
        }
    }
    public void deliver() {
        for (var job : jobs.findByTelegramQueuedFalseAndChannelInAndStatusNotIn(List.of("telegram", "report"), List.of("queued", "running"), PageRequest.of(0, settings.limits().queueSize()))) {
            var user = principal(job.userId); var p = preferences.findById(job.userId).orElse(null);
            if (user == null || p == null || !p.telegram || !notifications.assistantAllowed(user)) { job.telegramQueued = true; jobs.save(job); continue; }
            String text = Set.of("completed", "incomplete").contains(job.status) && job.outputText != null && !job.outputText.isBlank() ? job.outputText
                    : job.reportSummary != null ? job.reportSummary : "Не удалось получить ответ. Можно попробовать позже или открыть диалог в GrowerHub.";
            var message = assistant.message(job.id, user);
            for (int start = 0, part = 0; start < text.length(); start += 3500, part++) {
                notifications.enqueueMessage(user, "assistant-answer:" + job.id + ":" + part, text.substring(start, Math.min(text.length(), start + 3500)), null);
            }
            for (var proposal : message.proposals()) if ("pending".equals(proposal.status())) {
                String compact = proposal.id().toString().replace("-", "");
                String keyboard = write(Map.of("inline_keyboard", List.of(List.of(
                        Map.of("text", "Подтвердить", "callback_data", "gha:" + compact + ":" + proposal.revision()),
                        Map.of("text", "Отменить", "callback_data", "ghr:" + compact + ":" + proposal.revision())))));
                String details = proposal.title() + "\nИзменение будет сохранено только после подтверждения.\n" + readablePayload(proposal);
                if (details.length() > 3900) {
                    details = proposal.title() + "\nУ предложения много настроек. Откройте его полностью и подтвердите в приложении: " + settings.siteUrl() + "/app/assistant/";
                    keyboard = null;
                }
                notifications.enqueueMessage(user, "assistant-proposal:" + proposal.id(), details, keyboard);
            }
            job.telegramQueued = true; jobs.save(job);
        }
        for (var p : proposals.findByTelegramQueuedFalseAndStatusIn(List.of("applied", "stale", "uncertain", "expired", "cancelled"), PageRequest.of(0, settings.limits().queueSize()))) {
            var job = jobs.findById(p.jobId).orElse(null); var user = principal(p.userId);
            if (job != null && "telegram".equals(job.channel) && user != null && notifications.assistantAllowed(user)) {
                String result = switch (p.status) {
                    case "applied" -> "device".equals(p.kind) ? "Команда передана устройству. Фактическое состояние смотрите на ферме." : "Изменение сохранено.";
                    case "stale" -> "Настройки изменились. Попросите новое предложение.";
                    case "uncertain" -> "Результат неизвестен. Повторная команда не отправлена. Проверьте состояние в GrowerHub.";
                    default -> "Предложение отменено или устарело.";
                };
                notifications.enqueueMessage(user, "assistant-result:" + p.id, p.title + "\n" + result, null);
            }
            p.telegramQueued = true; proposals.save(p);
        }
        if (!settings.publicEnabled() || settings.ownerUserId() == null || settings.ownerUserId() <= 0) return;
        var user = principal(settings.ownerUserId()); if (user == null || !user.isAdmin()) return;
        var usage = assistant.usage(user); long max = settings.budget().monthMicros();
        int level = !usage.publicEnabled() || usage.monthMicros() + settings.budget().turnMicros() > max ? 100 : usage.monthMicros() * 100 / max >= 80 ? 80 : 0;
        var guard = guards.lock(1).orElseThrow(); String key = YearMonth.from(now()) + ":" + level;
        if (level > 0 && !key.equals(guard.alertKey) && !(YearMonth.from(now()) + ":100").equals(guard.alertKey)) {
            if (notifications.enqueueMessage(user, "assistant-budget:" + key, "Помощник GrowerHub: " + (level == 100 ? "публичные ответы приостановлены или достигнут месячный предел." : "использовано более 80% месячного бюджета.") + " Учтено $" + String.format(Locale.ROOT, "%.3f", usage.monthMicros() / 1000000d) + " из $" + String.format(Locale.ROOT, "%.2f", max / 1000000d) + ". Автоматика продолжает работать.", null)) guard.alertKey = key;
        }
    }
    private String readablePayload(Proposal proposal) {
        var p = proposal.payload();
        if ("care".equals(proposal.kind())) return p.path("action").asText() + ": " + p.path("text").asText();
        if ("reminder".equals(proposal.kind())) return p.path("title").asText() + ", " + p.path("dueAt").asText() + " UTC";
        if ("rule".equals(proposal.kind())) {
            var d = p.path("definition"); var trigger = d.path("trigger");
            String when = switch (trigger.path("type").asText()) {
                case "schedule" -> "В " + trigger.path("at").asText() + (trigger.path("days").size() == 0 || trigger.path("days").size() == 7 ? ", каждый день" : ", дни " + trigger.path("days").toString());
                case "interval" -> "Каждые " + trigger.path("seconds").asInt() + " сек., начало " + trigger.path("anchor").asText();
                default -> "При изменении показаний №" + trigger.path("resourceId").asInt() + ", свежесть до " + trigger.path("maxAgeSeconds").asInt() + " сек.";
            };
            var actions = new ArrayList<String>();
            for (var a : d.path("actions")) actions.add(switch (a.path("type").asText()) {
                case "set" -> resourceName(p, a) + ": " + a.path("value").asText();
                case "water" -> resourceName(p, a) + ": полив " + a.path("durationSeconds").asInt() + " секунд";
                case "delay" -> "Пауза " + a.path("seconds").asInt() + " секунд";
                default -> "Уведомление: " + a.path("text").asText();
            });
            return p.path("scopeName").asText("") + "\n" + (p.path("enabled").asBoolean() ? "Сценарий будет включён." : "Сценарий сохранится выключенным.") + "\n" + when + "\nУсловия: " + condition(d.path("condition"))
                    + "\n" + String.join(" → ", actions) + "\nПауза между запусками: " + d.path("cooldownSeconds").asInt() + " сек.";
        }
        if ("device".equals(proposal.kind())) return "water".equals(p.path("type").asText()) ? "Полив " + p.path("durationSeconds").asInt() + " секунд" : "Состояние: " + p.path("value").asText();
        return "Состояние: " + (p.path("enabled").asBoolean() ? "включён" : "выключен") + ". Настройки:\n" + p.path("config").toPrettyString();
    }
    private String resourceName(com.fasterxml.jackson.databind.JsonNode payload, com.fasterxml.jackson.databind.JsonNode action) {
        String id = action.path("resourceId").asText();
        return payload.path("resourceNames").path(id).asText("Устройство №" + id);
    }
    private String condition(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return "без дополнительных условий";
        if (Set.of("all", "any").contains(node.path("type").asText())) {
            var parts = new ArrayList<String>(); for (var c : node.path("conditions")) parts.add(condition(c));
            return "(" + String.join("all".equals(node.path("type").asText()) ? " И " : " ИЛИ ", parts) + ")";
        }
        return switch (node.path("type").asText()) {
            case "not" -> "НЕ (" + condition(node.path("condition")) + ")";
            case "stage" -> "Растение №" + node.path("plantId").asInt() + ", стадия " + node.path("stage").asText();
            case "time" -> "Время " + node.path("from").asText() + "–" + node.path("until").asText() + ", дни " + node.path("days").toString();
            default -> "Датчик №" + node.path("resourceId").asInt() + " " + Map.of("gt", ">", "gte", "≥", "lt", "<", "lte", "≤", "eq", "=", "ne", "≠").getOrDefault(node.path("op").asText(), "?")
                    + " " + node.path("value").asText() + "; выдержка " + node.path("holdSeconds").asInt() + " сек., гистерезис " + node.path("hysteresis").asDouble()
                    + ", показания не старше " + node.path("maxAgeSeconds").asInt() + " сек.";
        };
    }
    private Conversation conversation(AssistantPreferencesEntity p, AuthenticatedUser user) {
        if (p.conversationId != null) {
            var c = conversations.findByIdAndUserId(p.conversationId, user.id()).orElse(null);
            if (c != null) return new Conversation(c.id, c.title, c.model, c.effort, c.updatedAt);
        }
        var c = assistant.conversations(user).stream().findFirst().orElseGet(() -> assistant.create(user, new Origin(null, null, "telegram"), new Options(null, null)));
        p.conversationId = c.id(); preferences.save(p); return c;
    }
    private String plainReport(AuthenticatedUser user) {
        var r = tools.report(user);
        return "Ваша ферма 🌱\nРастений: " + r.path("plantCount").asInt() + "\nФерм: " + r.path("farmCount").asInt() + ", теплиц: " + r.path("greenhouseCount").asInt()
                + "\nУстройств в зонах: " + r.path("resourceCount").asInt() + ", требуют внимания: " + r.path("notReadyCount").asInt()
                + "\nНапоминаний по уходу: " + r.path("reminders").size() + "\nСостояние на " + r.path("at").asText() + " UTC.";
    }
    private AuthenticatedUser principal(Integer id) { var u = users.getAuthUser(id); return u == null || !u.active() ? null : new AuthenticatedUser(id, u.role()); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
