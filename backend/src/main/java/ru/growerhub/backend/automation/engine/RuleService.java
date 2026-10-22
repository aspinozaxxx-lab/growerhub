package ru.growerhub.backend.automation.engine;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.automation.jpa.*;
import ru.growerhub.backend.common.config.automation.RuleSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@Service
public class RuleService {
    private final RuleRepository rules;
    private final RuleVersionRepository versions;
    private final RuleRunRepository runs;
    private final RuleEvaluator evaluator;
    private final RuleSettings settings;
    private final ObjectMapper json;
    private final Clock clock;
    private final UserFacade users;
    private final PlantFacade plants;
    private final NotificationFacade notifications;
    private final ZigbeeFacade zigbee;
    private final ObjectProvider<AutomationFacade> automation;
    private final ControlService controls;
    public RuleService(RuleRepository rules, RuleVersionRepository versions, RuleRunRepository runs, RuleEvaluator evaluator,
            RuleSettings settings, ObjectMapper json, Clock clock, UserFacade users, PlantFacade plants,
            NotificationFacade notifications, ZigbeeFacade zigbee, ObjectProvider<AutomationFacade> automation, ControlService controls) {
        this.rules = rules; this.versions = versions; this.runs = runs; this.evaluator = evaluator; this.settings = settings;
        this.json = json; this.clock = clock; this.users = users; this.plants = plants;
        this.notifications = notifications; this.zigbee = zigbee; this.automation = automation;
        this.controls = controls;
    }
    private record Snapshot(FarmsOverview farms, RuleEvaluator.Context context, Map<String, Boolean> areas) {}
    public List<Rule> list(AuthenticatedUser user) { require(user); return rules.findByUserIdOrderByUpdatedAtDesc(user.id()).stream().map(this::view).toList(); }
    public Map<String, Object> schema() { return evaluator.schema(); }
    public Rule get(UUID id, AuthenticatedUser user) { return view(owned(id, user)); }
    public List<Version> versions(UUID id, AuthenticatedUser user) {
        owned(id, user); return versions.findByRuleIdOrderByRevisionDesc(id).stream().map(v -> new Version(v.revision, v.enabled, definition(v.definitionJson), v.createdAt)).toList();
    }
    public List<Run> history(UUID id, AuthenticatedUser user) {
        owned(id, user); return runs.findByRuleIdOrderByCreatedAtDesc(id, PageRequest.of(0, settings.historyLimit())).stream()
                .map(r -> new Run(r.id, r.revision, r.status, r.stepIndex, r.reason, read(r.resultJson), r.createdAt, r.nextAt)).toList();
    }
    public Preview preview(UUID id, Definition definition, AuthenticatedUser user) {
        if (id != null) owned(id, user);
        return preview(id, definition, user, snapshot(user));
    }
    private Preview preview(UUID id, Definition d, AuthenticatedUser user, Snapshot s) {
        Set<Integer> resources = evaluator.validate(d, s.context());
        if (!s.areas().containsKey(area(d))) throw bad("Ферма или теплица не найдена");
        Set<Integer> areaResources = areaResources(d, s.farms());
        if (!areaResources.containsAll(resources)) throw bad("Устройства должны относиться к выбранной ферме или теплице");
        if (d.version() >= 2) for (var action : d.actions()) if ("water".equals(action.path("type").asText()))
            automation.getObject().validateRuleWatering(watering(action), user);
        var readiness = new ArrayList<String>();
        if (!s.areas().get(area(d))) readiness.add("Выбранная ферма или теплица отключена");
        for (int resourceId : resources) {
            var resource = s.context().resources().get(resourceId);
            if (!resource.ready()) readiness.add(resource.label() + ": " + Objects.requireNonNullElse(resource.reason(), "нет связи"));
        }
        Set<Integer> writes = writes(d); var conflicts = new ArrayList<String>();
        for (var existing : rules.findByUserIdOrderByUpdatedAtDesc(user.id()))
            if (existing.enabled && !existing.id.equals(id) && !(cooperative(d) && cooperative(definition(existing.definitionJson))) && !Collections.disjoint(writes, writes(definition(existing.definitionJson))))
                conflicts.add("Канал уже использует правило «" + definition(existing.definitionJson).name() + "»");
        for (var farm : s.farms().farms()) {
            for (var scenario : farm.scenarios()) if (!cooperative(d) && scenario.enabled() && !Collections.disjoint(writes, fixedWrites(scenario, s.farms())))
                conflicts.add("Канал уже использует сценарий «" + scenarioName(scenario.scenarioType()) + "» фермы «" + farm.name() + "»");
            for (var greenhouse : farm.greenhouses()) for (var scenario : greenhouse.scenarios())
                if (!cooperative(d) && scenario.enabled() && !Collections.disjoint(writes, fixedWrites(scenario, s.farms())))
                    conflicts.add("Канал уже использует сценарий «" + scenarioName(scenario.scenarioType()) + "» теплицы «" + greenhouse.name() + "»");
        }
        var coordination = new ArrayList<String>();
        if (cooperative(d)) {
            for (var action : d.actions()) if ("hold".equals(action.path("type").asText())) {
                coordination.add("Удержание устройства №" + action.path("resourceId").asInt() + ": приоритет " + action.path("priority").asInt()
                        + ", " + action.path("durationSeconds").asInt() + " сек. после фактического подтверждения. По окончании управление возвращается актуальным требованиям.");
                int priority = action.path("priority").asInt();
                for (var farm : s.farms().farms()) {
                    for (var scenario : farm.scenarios()) if (scenario.enabled() && fixedWrites(scenario, s.farms()).contains(action.path("resourceId").asInt()))
                        coordination.add(relation(scenarioName(scenario.scenarioType()) + ": " + farm.name(), controls.scenarioPriority(), priority));
                    for (var greenhouse : farm.greenhouses()) for (var scenario : greenhouse.scenarios())
                        if (scenario.enabled() && fixedWrites(scenario, s.farms()).contains(action.path("resourceId").asInt()))
                            coordination.add(relation(scenarioName(scenario.scenarioType()) + ": " + greenhouse.name(), controls.scenarioPriority(), priority));
                }
            }
            for (var existing : rules.findByUserIdOrderByUpdatedAtDesc(user.id()))
                if (existing.enabled && !existing.id.equals(id) && !Collections.disjoint(writes, writes(definition(existing.definitionJson))))
                    coordination.add("На этих каналах также работает «" + definition(existing.definitionJson).name() + "»: выигрывает больший приоритет, равные — в неизменном порядке создания и ключа.");
            coordination.add("Удержание согласуется с готовыми сценариями. Ручное временное управление выше пользовательских правил. Аппаратные защиты и остановка полива не переопределяются.");
        }
        return new Preview(true, describe(d), conflicts.stream().distinct().toList(), readiness, List.copyOf(resources), evaluator.timing(d, s.context()), List.copyOf(coordination));
    }
    private String relation(String source, int priority, int holdPriority) {
        return "«" + source + "»: приоритет " + priority + ". Удержание с приоритетом " + holdPriority
                + (holdPriority > priority ? " будет выше этого сценария." : holdPriority < priority ? " уступит этому сценарию."
                : " имеет равный приоритет: выигрывает более раннее неизменное время создания, затем ключ источника.");
    }
    public Rule save(UUID id, Save command, AuthenticatedUser user) {
        lock(user);
        if (command == null) throw bad("Заполните правило");
        var existing = id == null ? null : owned(id, user);
        if (existing != null && !Objects.equals(command.revision(), existing.revision)) throw conflict();
        if (existing == null && rules.countByUserId(user.id()) >= settings.maximum()) throw bad("Достигнут лимит правил");
        var check = preview(id, command.definition(), user);
        if (command.enabled() && !check.conflicts().isEmpty()) throw bad(String.join(". ", check.conflicts()));
        if (command.enabled() && !check.readiness().isEmpty()) throw bad(String.join(". ", check.readiness()));
        var row = existing == null ? new RuleEntity() : existing;
        if (existing == null) { row.id = UUID.randomUUID(); row.userId = user.id(); row.createdAt = now(); }
        row.revision++; row.enabled = command.enabled(); row.definitionJson = write(command.definition());
        row.runtimeJson = "{}"; row.updatedAt = now(); rules.save(row);
        var version = new RuleVersionEntity(); version.id = UUID.randomUUID(); version.ruleId = row.id; version.revision = row.revision;
        version.enabled = row.enabled; version.definitionJson = row.definitionJson; version.createdAt = now(); versions.save(version);
        cancelWaiting(row.id, user.id(), "Правило изменено"); return view(row);
    }
    public Rule toggle(UUID id, Toggle command, AuthenticatedUser user) {
        lock(user);
        var row = owned(id, user); return save(id, new Save(command.revision(), command.enabled(), definition(row.definitionJson)), user);
    }
    public Rule restore(UUID id, Restore command, AuthenticatedUser user) {
        lock(user);
        owned(id, user); var version = versions.findByRuleIdAndRevision(id, command.sourceRevision()).orElseThrow(() -> bad("Версия не найдена"));
        return save(id, new Save(command.revision(), false, definition(version.definitionJson)), user);
    }
    public void disableAll(AuthenticatedUser user) {
        lock(user);
        for (var rule : rules.findByUserIdOrderByUpdatedAtDesc(user.id()))
            if (rule.enabled) save(rule.id, new Save(rule.revision, false, definition(rule.definitionJson)), user);
    }
    public void requireFixedChannelAvailable(AuthenticatedUser user, String scope, int scopeId, String type) {
        lock(user);
        var s = snapshot(user);
        var targets = fixedWrites(new ScenarioConfig(null, scope, scopeId, type, true, Map.of(), null, null, null), s.farms());
        for (var rule : rules.findByUserIdOrderByUpdatedAtDesc(user.id()))
            if (rule.enabled && !cooperative(definition(rule.definitionJson)) && !Collections.disjoint(targets, writes(definition(rule.definitionJson))))
                throw bad("Сначала отключите правило «" + definition(rule.definitionJson).name() + "», которое управляет этим каналом");
    }
    public List<Integer> ownersDue() {
        return Stream.concat(rules.findByEnabledTrue().stream().map(r -> r.userId),
                Stream.concat(runs.findByStatusOrderByNextAtAsc("waiting", PageRequest.of(0, settings.maximum())).stream().map(r -> r.userId),
                        runs.findByStatusOrderByNextAtAsc("dispatching", PageRequest.of(0, settings.maximum())).stream().map(r -> r.userId))).distinct().toList();
    }
    public void evaluate(Integer ownerId) {
        var user = principal(ownerId); if (user == null) return; lock(user);
        var s = snapshot(user);
        for (var row : rules.findByUserIdOrderByUpdatedAtDesc(ownerId)) {
            if (!row.enabled) continue;
            var d = definition(row.definitionJson); var runtime = (ObjectNode) read(row.runtimeJson);
            try {
                var preview = preview(row.id, d, user, s);
                var condition = evaluator.conditions(d, s.context(), runtime);
                if (d.version() >= 3 && (!condition.valid() || !condition.value())
                        && (!condition.valid() || !"finish".equals(d.trigger().path("onConditionLoss").asText()))) {
                    controls.releaseRule(ownerId, row.id, condition.reason());
                    for (var running : runs.findByUserIdAndStatusIn(ownerId, List.of("waiting", "dispatching")))
                        if (running.ruleId.equals(row.id)) stop(running, condition.reason());
                }
                var decision = evaluator.evaluate(d, s.context(), runtime);
                String key = row.revision + ":" + decision.key();
                if (!preview.conflicts().isEmpty() || !preview.readiness().isEmpty()) {
                    runtime.put("reason", String.join(". ", Stream.concat(preview.conflicts().stream(), preview.readiness().stream()).toList()));
                } else if (decision.key() == null || !decision.ready()) runtime.put("reason", decision.reason());
                else if (!runs.existsByRuleIdAndTriggerKey(row.id, key)
                        && runs.findByUserIdAndStatusIn(ownerId, List.of("waiting", "dispatching")).stream().noneMatch(r -> r.ruleId.equals(row.id))) {
                    dailyWaterLimit(d, ownerId);
                    var run = new RuleRunEntity(); run.id = UUID.randomUUID(); run.ruleId = row.id; run.userId = ownerId;
                    run.revision = row.revision; run.triggerKey = key; run.definitionJson = row.definitionJson;
                    run.bindingFingerprint = fingerprint(d, user); run.status = "waiting"; run.resultJson = "[]";
                    run.nextAt = now(); run.createdAt = now(); run.reason = "Условия выполнены"; runs.save(run);
                    runtime.put("lastStarted", clock.instant().getEpochSecond()); runtime.put("reason", "Последовательность запущена");
                }
            } catch (DomainException ex) { runtime.put("reason", ex.getMessage()); }
            row.runtimeJson = runtime.toString(); rules.save(row);
        }
    }
    public Step claim(Integer ownerId) {
        var user = principal(ownerId); if (user == null) return null; lock(user);
        for (var run : runs.findByUserIdAndStatusIn(ownerId, List.of("waiting", "dispatching"))) {
            var d = definition(run.definitionJson);
            if ("dispatching".equals(run.status)) {
                if (run.leasedUntil == null || !run.leasedUntil.isAfter(now())) {
                    if (waitingWater(d, run)) resumeWatering(run, user);
                    else if (waitingControl(d, run)) resumeControl(run, user);
                    else { run.status = "uncertain"; run.reason = "Результат команды неизвестен. Повтор автоматически не отправлен"; runs.save(run); }
                }
                continue;
            }
            if (run.nextAt.isAfter(now())) continue;
            var rule = rules.findById(run.ruleId).orElse(null);
            if (rule == null || !rule.enabled || rule.revision != run.revision) { stop(run, "Правило изменено или отключено"); continue; }
            var results = read(run.resultJson);
            if (waitingControl(d, run) && !results.isEmpty() && results.get(results.size() - 1).has("waitingForControl")) {
                resumeControl(run, user); continue;
            }
            if (waitingWater(d, run) && !results.isEmpty() && results.get(results.size() - 1).has("waitingForWatering")) {
                resumeWatering(run, user); continue;
            }
            try {
                if (!run.bindingFingerprint.equals(fingerprint(d, user))) { stop(run, "Состав оборудования изменился"); continue; }
                var check = preview(rule.id, d, user);
                if (!check.conflicts().isEmpty() || !check.readiness().isEmpty()) { stop(run, "Оборудование или настройки недоступны"); continue; }
            } catch (DomainException ex) { stop(run, ex.getMessage()); continue; }
            if (run.stepIndex >= d.actions().size()) { run.status = "completed"; run.reason = "Все действия завершены"; runs.save(run); continue; }
            var action = d.actions().get(run.stepIndex);
            if ("delay".equals(action.path("type").asText())) {
                run.stepIndex++; run.nextAt = (d.version() >= 2 ? run.nextAt : now()).plusSeconds(action.get("seconds").asInt()); run.reason = "Пауза"; runs.save(run); continue;
            }
            run.status = "dispatching"; run.leaseKey = UUID.randomUUID().toString(); run.leasedUntil = now().plusSeconds(settings.leaseSeconds());
            run.reason = "Команда подготовлена"; runs.save(run);
            return new Step(run.id, run.leaseKey, user, action, run.bindingFingerprint);
        }
        return null;
    }
    public JsonNode execute(Step step) {
        lock(step.user()); var run = lease(step); var rule = rules.findById(run.ruleId).orElseThrow(RuleService::conflict);
        if (!rule.enabled || rule.revision != run.revision || !run.bindingFingerprint.equals(fingerprint(definition(run.definitionJson), step.user()))) throw conflict();
        var action = definition(run.definitionJson).actions().get(run.stepIndex);
        if ("notify".equals(action.get("type").asText())) {
            boolean sent = notifications.enqueueMessage(step.user(), "rule:" + run.id + ":" + run.stepIndex, action.get("text").asText(), null);
            return json.valueToTree(Map.of("notificationQueued", sent, "demo", step.user().isDemo()));
        }
        if (waitingWater(definition(run.definitionJson), run)) {
            var session = automation.getObject().startRuleWatering(watering(action), executionKey(run), step.user());
            return json.valueToTree(Map.of("waitingForWatering", session.id()));
        }
        if (waitingControl(definition(run.definitionJson), run)) {
            var r = snapshot(step.user()).context().resources().get(action.get("resourceId").asInt());
            UUID id;
            if ("hold".equals(action.get("type").asText())) {
                if (!controlConditionAllowed(run.ruleId, run.revision, step.user(), false)) throw bad("Условие удержания больше не выполнено");
                id = controls.ruleHold(r, step.user(), run.ruleId, definition(run.definitionJson).name(), executionKey(run),
                        json.convertValue(action.get("value"), Object.class), action.get("durationSeconds").asInt(), action.get("priority").asInt(), Integer.toString(run.revision));
            } else id = controls.legacy(r, step.user(), run.ruleId, definition(run.definitionJson).name(), executionKey(run), json.convertValue(action.get("value"), Object.class), Integer.toString(run.revision));
            return json.valueToTree(Map.of("waitingForControl", id));
        }
        return command(action, step.user());
    }
    public void finish(Step step, JsonNode result, boolean success) {
        lock(step.user()); var run = lease(step);
        var history = (com.fasterxml.jackson.databind.node.ArrayNode) read(run.resultJson); history.add(result);
        run.resultJson = history.toString();
        boolean water = waitingWater(definition(run.definitionJson), run);
        if (waitingControl(definition(run.definitionJson), run)) {
            if (!success) { resumeControl(run, step.user()); return; }
            run.status = "waiting"; run.reason = "Ожидает фактическое состояние и завершение удержания";
            run.nextAt = now(); run.leasedUntil = null; runs.save(run); return;
        }
        if (water && !success) { resumeWatering(run, step.user()); return; }
        if (!water || !success) run.stepIndex++;
        run.status = success ? "waiting" : "uncertain";
        run.reason = success ? "Действие передано устройству" : "Не удалось подтвердить выполнение. Повтор не отправлен";
        run.nextAt = now(); run.leasedUntil = null; runs.save(run);
    }

    private Watering watering(JsonNode action) {
        var pulse = action.path("pulse");
        return new Watering(action.get("resourceId").asInt(), action.get("durationSeconds").asInt(),
                pulse.isObject() ? pulse.get("runSeconds").asInt() : null, pulse.isObject() ? pulse.get("pauseSeconds").asInt() : null);
    }
    private boolean waitingWater(Definition d, RuleRunEntity run) {
        return d.version() >= 2 && run.stepIndex < d.actions().size() && "water".equals(d.actions().get(run.stepIndex).path("type").asText());
    }
    private boolean waitingControl(Definition d, RuleRunEntity run) {
        return run.stepIndex < d.actions().size() && Set.of("set", "hold").contains(d.actions().get(run.stepIndex).path("type").asText());
    }
    public boolean controlConditionAllowed(UUID ruleId, int revision, AuthenticatedUser user, boolean started) {
        var rule = owned(ruleId, user);
        if (!rule.enabled || rule.revision != revision) return false;
        var definition = definition(rule.definitionJson);
        var check = evaluator.conditions(definition, snapshot(user).context(), (ObjectNode) read(rule.runtimeJson));
        return check.valid() && (check.value() || started && "finish".equals(definition.trigger().path("onConditionLoss").asText()));
    }
    private void resumeControl(RuleRunEntity run, AuthenticatedUser user) {
        var demand = controls.findExecution(executionKey(run), user); run.leasedUntil = null;
        if (demand == null) { run.status = "uncertain"; run.reason = "Результат подготовки команды неизвестен. Команда не повторена"; runs.save(run); return; }
        boolean hold = "hold".equals(definition(run.definitionJson).actions().get(run.stepIndex).path("type").asText());
        if ("error".equals(demand.status())) { run.status = "failed"; run.reason = Objects.requireNonNullElse(demand.error(), "Устройство не подтвердило команду"); }
        else if ((!hold && demand.confirmedAt() != null) || hold && demand.expiresAt() != null && !demand.expiresAt().isAfter(now())
                && "released".equals(demand.status()) && "Срок удержания закончился".equals(demand.reason())) {
            var history = (com.fasterxml.jackson.databind.node.ArrayNode) read(run.resultJson);
            if (!history.isEmpty() && history.get(history.size() - 1).has("waitingForControl")) history.remove(history.size() - 1);
            history.add(json.valueToTree(Map.of("controlId", demand.id(), "confirmedAt", demand.confirmedAt(), "completed", true)));
            run.resultJson = history.toString(); run.status = "waiting"; run.stepIndex++;
            run.nextAt = hold ? demand.expiresAt() : demand.confirmedAt(); run.reason = hold ? "Удержание завершено, остальные требования переоценены" : "Состояние устройства подтверждено";
        } else if ("released".equals(demand.status())) {
            run.status = demand.error() == null ? "cancelled" : "partial"; run.reason = demand.reason();
            if (demand.error() != null) {
                var history = (com.fasterxml.jackson.databind.node.ArrayNode) read(run.resultJson);
                history.add(json.valueToTree(Map.of("controlId", demand.id(), "completed", false, "interrupted", true, "reason", demand.error())));
                run.resultJson = history.toString();
            }
        }
        else { run.status = "waiting"; run.reason = "blocked".equals(demand.status()) ? "Ожидает более приоритетное управление" : demand.confirmedAt() == null ? "Ожидает фактическое подтверждение устройства" : "Удерживает состояние до " + demand.expiresAt(); run.nextAt = now().plusNanos(settings.workerMs() * 1000000L); }
        runs.save(run);
    }
    private String executionKey(RuleRunEntity run) { return "rule:" + run.id + ":" + run.stepIndex; }
    private void resumeWatering(RuleRunEntity run, AuthenticatedUser user) {
        var session = automation.getObject().ruleWateringExecution(executionKey(run), user);
        run.leasedUntil = null;
        if (session == null) {
            run.status = "uncertain"; run.reason = "Результат запуска полива неизвестен. Команда не повторена"; runs.save(run); return;
        }
        var history = (com.fasterxml.jackson.databind.node.ArrayNode) read(run.resultJson);
        if (!history.isEmpty() && history.get(history.size() - 1).has("waitingForWatering")) history.remove(history.size() - 1);
        if (PumpSessionData.STATUS_ACTIVE.equals(session.status())) {
            history.add(json.valueToTree(Map.of("waitingForWatering", session.id())));
            run.status = "waiting"; run.reason = "Ожидает завершения полива"; run.nextAt = now().plusNanos(settings.workerMs() * 1000000L);
            if (now().isAfter(run.createdAt.plusSeconds(settings.maxDelaySeconds() + settings.leaseSeconds()))) {
                automation.getObject().stopRuleWatering(executionKey(run), user);
                run.status = "uncertain"; run.reason = "Время ожидания полива истекло. Запрошена остановка, следующие действия отменены";
            }
        } else {
            history.add(json.valueToTree(Map.of("sessionId", session.id(), "phase", session.phase(), "activeSeconds", session.activeDurationS(),
                    "completionReason", Objects.requireNonNullElse(session.completionReason(), "unknown"))));
            boolean complete = PumpSessionData.PHASE_COMPLETED.equals(session.phase()) && PumpSessionData.REASON_DURATION.equals(session.completionReason())
                    && session.activeDurationS() >= watering(definition(run.definitionJson).actions().get(run.stepIndex)).durationSeconds();
            run.status = complete ? "waiting" : "failed";
            run.reason = complete ? "Полив завершён, следующий шаг разрешён" : "Полив остановлен без полного завершения. Последовательность прекращена";
            if (complete) run.stepIndex++;
            run.nextAt = session.finishedAt() == null ? now() : session.finishedAt();
        }
        run.resultJson = history.toString(); runs.save(run);
    }
    public JsonNode validateCommand(JsonNode action, AuthenticatedUser user) {
        var s = snapshot(user); var r = s.context().resources().get(action.path("resourceId").asInt());
        if (r == null) throw bad("Устройство не найдено");
        var validation = action.deepCopy();
        if (validation instanceof ObjectNode object) object.remove(List.of("ttlSeconds", "requestId"));
        var d = new Definition(1, "Ручная команда", r.scopeType(), r.scopeId(), json.valueToTree(Map.of("type", "schedule", "at", "00:00")), null, List.of(validation), settings.minCooldownSeconds());
        evaluator.validate(d, s.context());
        if ("set".equals(action.path("type").asText()) && action.has("ttlSeconds")) {
            if (!action.get("ttlSeconds").isIntegralNumber() || !action.get("ttlSeconds").canConvertToInt()) throw bad("Укажите срок ручного управления целым числом секунд");
            controls.ttl(action.get("ttlSeconds").asInt());
        }
        if (!s.areas().getOrDefault(area(d), false) || !r.ready()) throw bad("Устройство сейчас недоступно");
        var shown = action.deepCopy();
        if ("set".equals(action.path("type").asText())) ((ObjectNode) shown).set("value", json.valueToTree(controls.normalizeValue(r, json.convertValue(action.path("value"), Object.class))));
        return json.valueToTree(Map.of("device", r.label(), "action", shown, "binding", binding(r)));
    }
    public JsonNode command(JsonNode action, AuthenticatedUser user) {
        lock(user); validateCommand(action, user);
        var r = snapshot(user).context().resources().get(action.get("resourceId").asInt());
        if ("water".equals(action.get("type").asText())) {
            int seconds = action.get("durationSeconds").asInt();
            var request = new ManualWateringStartRequest(PumpSessionData.MODE_TIMED, seconds, seconds, false, null, null);
            return json.valueToTree(r.nativePumpId() != null ? automation.getObject().startUserManualWateringSession(r.nativePumpId(), request, user)
                    : automation.getObject().startResourceWatering(r.id(), request, user));
        }
        Object value = json.convertValue(action.get("value"), Object.class);
        var requestId = action.hasNonNull("requestId") ? UUID.fromString(action.get("requestId").asText()) : UUID.randomUUID();
        return json.valueToTree(controls.manual(r.id(), new ru.growerhub.backend.automation.contract.ControlData.Manual(requestId, value,
                action.has("ttlSeconds") ? action.get("ttlSeconds").asInt() : controls.defaultManualTtlSeconds()), user));
    }
    public String fingerprint(Definition d, AuthenticatedUser user) {
        var s = snapshot(user); Set<Integer> ids = evaluator.validate(d, s.context());
        return digest(write(List.of(d, ids.stream().map(s.context().resources()::get).map(this::binding).toList(), s.areas(), s.context().timezone().getId())));
    }
    public String scenarioFingerprint(String scope, int id, AuthenticatedUser user) {
        var s = snapshot(user);
        var resources = s.context().resources().values().stream().sorted(Comparator.comparing(ResourceBinding::id)).map(this::binding).toList();
        var scenario = s.farms().farms().stream().flatMap(f -> Stream.concat(f.scenarios().stream(), f.greenhouses().stream().flatMap(g -> g.scenarios().stream())))
                .filter(c -> c.scopeType().equals(scope) && c.scopeId().equals(id)).toList();
        return digest(write(List.of(scenario, resources, s.areas())));
    }
    private void dailyWaterLimit(Definition d, Integer owner) {
        var reserved = new HashMap<Integer, Integer>();
        for (var run : runs.findByUserIdAndCreatedAtAfter(owner, now().minusDays(1)))
            for (var a : definition(run.definitionJson).actions()) if ("water".equals(a.path("type").asText())) reserved.merge(a.get("resourceId").asInt(), a.get("durationSeconds").asInt(), Integer::sum);
        for (var a : d.actions()) if ("water".equals(a.path("type").asText())) {
            int sum = reserved.merge(a.get("resourceId").asInt(), a.get("durationSeconds").asInt(), Integer::sum);
            if (sum > settings.maxDailyWateringSeconds()) throw bad("Суточный лимит полива этого канала исчерпан");
        }
    }
    private Set<Integer> fixedWrites(ScenarioConfig c, FarmsOverview overview) {
        Set<String> roles = switch (c.scenarioType()) {
            case "LIGHT_SCHEDULE" -> Set.of("LIGHT_SWITCH"); case "WATERING" -> Set.of("WATER_PUMP");
            case "BOX_CLIMATE" -> Set.of("EXHAUST_SWITCH", "AC_SWITCH"); case "ROOM_CLIMATE" -> Set.of("AC_SWITCH"); default -> Set.of(); };
        return overview.farms().stream().flatMap(f -> {
            if ("ROOM".equals(c.scopeType()) && f.id().equals(c.scopeId())) return f.slots().stream();
            return f.greenhouses().stream().filter(g -> "BOX".equals(c.scopeType()) && g.id().equals(c.scopeId()))
                    .flatMap(g -> Stream.concat(g.slots().stream(), "BOX_CLIMATE".equals(c.scenarioType()) ? f.slots().stream() : Stream.empty()));
        }).filter(r -> roles.contains(r.role())).map(ResourceBinding::id).collect(Collectors.toSet());
    }
    private Set<Integer> writes(Definition d) { return d.actions().stream().filter(a -> Set.of("set", "water", "hold").contains(a.path("type").asText())).map(a -> a.path("resourceId").asInt()).collect(Collectors.toSet()); }
    private boolean cooperative(Definition d) { return d.version() >= 3 && d.actions().stream().noneMatch(a -> Set.of("set", "water").contains(a.path("type").asText())); }
    private Set<Integer> areaResources(Definition d, FarmsOverview f) {
        return f.farms().stream().flatMap(farm -> "ROOM".equals(d.scopeType()) && farm.id().equals(d.scopeId())
                ? Stream.concat(farm.slots().stream(), farm.greenhouses().stream().flatMap(g -> g.slots().stream()))
                : farm.greenhouses().stream().filter(g -> "BOX".equals(d.scopeType()) && g.id().equals(d.scopeId())).flatMap(g -> g.slots().stream())).map(ResourceBinding::id).collect(Collectors.toSet());
    }
    private Snapshot snapshot(AuthenticatedUser user) {
        require(user); var f = automation.getObject().getFarmsOverview(user); var resources = new TreeMap<Integer, ResourceBinding>(); var areas = new TreeMap<String, Boolean>();
        for (var farm : f.farms()) {
            areas.put("ROOM:" + farm.id(), farm.enabled()); farm.slots().forEach(r -> resources.put(r.id(), r));
            for (var g : farm.greenhouses()) { areas.put("BOX:" + g.id(), farm.enabled() && g.enabled()); g.slots().forEach(r -> resources.put(r.id(), r)); }
        }
        resources.replaceAll((id, r) -> {
            if (!controls.switchChannel(r) || !r.ready()) return r;
            ru.growerhub.backend.zigbee.contract.ZigbeeControlObservation observed;
            boolean available = true;
            try { observed = zigbee.controlObservation(user, r.zigbeeCoordinatorId(), r.zigbeeIeeeAddress(), r.commandProperty()); }
            catch (DomainException ex) { observed = null; available = false; }
            return new ResourceBinding(r.id(), r.scopeType(), r.scopeId(), r.role(), r.sourceType(), r.nativeSensorId(), r.nativePumpId(),
                    r.zigbeeCoordinatorId(), r.zigbeeIeeeAddress(), r.zigbeeProperty(), r.commandProperty(), r.onValue(), r.offValue(),
                    observed == null ? null : observed.value(), observed == null ? null : observed.observedAt(), r.connectionStatus(), r.connectionMessage(),
                    r.label(), available && r.ready() && (observed == null || observed.online()), available ? r.reason() : "Устройство сейчас недоступно");
        });
        var p = plants.listPlants(user).stream().collect(Collectors.toMap(v -> v.id(), v -> v));
        return new Snapshot(f, new RuleEvaluator.Context(resources, p, ZoneId.of(users.getTimezone(user.id())), clock.instant()), areas);
    }
    private JsonNode binding(ResourceBinding r) {
        var node = (ObjectNode) json.valueToTree(r); node.remove(List.of("current_value", "last_seen_at", "connection_status", "connection_message", "ready", "reason")); return node;
    }
    private RuleRunEntity lease(Step step) {
        var run = runs.findById(step.runId()).orElseThrow(RuleService::conflict);
        if (!run.userId.equals(step.user().id()) || !"dispatching".equals(run.status) || !Objects.equals(run.leaseKey, step.lease()) || run.leasedUntil.isBefore(now())) throw conflict(); return run;
    }
    private void cancelWaiting(UUID rule, Integer owner, String reason) {
        controls.releaseRule(owner, rule, reason);
        for (var r : runs.findByUserIdAndStatusIn(owner, List.of("waiting", "dispatching"))) if (r.ruleId.equals(rule)) {
            var d = definition(r.definitionJson);
            if (waitingWater(d, r)) automation.getObject().stopRuleWatering(executionKey(r), principal(owner));
            if (d.version() >= 2 || "waiting".equals(r.status)) stop(r, reason);
        }
    }
    private void stop(RuleRunEntity r, String reason) { r.status = "cancelled"; r.reason = reason; runs.save(r); }
    private AuthenticatedUser principal(Integer id) {
        if (users.isDemoOwner(id)) return new AuthenticatedUser(id, "demo");
        var u = users.getAuthUser(id); return u == null || !u.active() ? null : new AuthenticatedUser(id, u.role());
    }
    private void lock(AuthenticatedUser user) { require(user); if (user.isDemo()) users.lockDemoOwner(user.id()); else users.lockAccount(user.id()); }
    private void require(AuthenticatedUser user) { if (user == null || user.id() == null) throw new DomainException("unauthorized", "Войдите в аккаунт"); }
    private RuleEntity owned(UUID id, AuthenticatedUser user) { require(user); return rules.findById(id).filter(r -> r.userId.equals(user.id())).orElseThrow(() -> new DomainException("not_found", "Правило не найдено")); }
    private Rule view(RuleEntity row) { var d = definition(row.definitionJson); return new Rule(row.id, row.revision, row.enabled, d, describe(d), row.updatedAt, row.enabled ? read(row.runtimeJson).path("reason").asText("Ожидает проверки условий") : "Выключен"); }
    private String describe(Definition d) {
        String when = switch (d.trigger().path("type").asText()) { case "schedule" -> "В " + d.trigger().path("at").asText(); case "interval" -> "Каждые " + d.trigger().path("seconds").asInt() + " сек.";
            case "repeat_while" -> "Пока выполняются условия, каждые " + d.trigger().path("seconds").asInt() + " сек.; первый цикл " + ("immediate".equals(d.trigger().path("first").asText()) ? "сразу" : "через период");
            case "calendar" -> "Каждые " + d.trigger().path("every").asInt() + ("week".equals(d.trigger().path("unit").asText()) ? " нед." : " дн.") + " в " + d.trigger().path("at").asText() + ", с " + d.trigger().path("startDate").asText(); default -> "При изменении показаний"; };
        return when + (d.condition() == null || d.condition().isNull() ? "" : ", если выполнены условия") + ": " + d.actions().stream().map(a -> switch (a.path("type").asText()) {
            case "set" -> "установить " + a.path("value").asText() + " на устройстве №" + a.path("resourceId").asInt();
            case "hold" -> "удерживать " + a.path("value").asText() + " " + a.path("durationSeconds").asInt() + " сек. с приоритетом " + a.path("priority").asInt() + " на устройстве №" + a.path("resourceId").asInt();
            case "water" -> "поливать " + a.path("durationSeconds").asInt() + " сек."; case "delay" -> "подождать " + a.path("seconds").asInt() + " сек."; default -> "отправить уведомление";
        }).collect(Collectors.joining(" → "));
    }
    private String area(Definition d) { return d.scopeType() + ":" + d.scopeId(); }
    private String scenarioName(String type) {
        return Map.of("ROOM_CLIMATE", "Климат фермы", "BOX_CLIMATE", "Климат", "LIGHT_SCHEDULE", "Освещение", "WATERING", "Полив").getOrDefault(type, type);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw bad("Неверные данные правила"); } }
    private JsonNode read(String value) { try { return json.readTree(value == null ? "{}" : value); } catch (Exception ex) { throw bad("Не удалось прочитать правило"); } }
    private Definition definition(String value) { try { return json.readValue(value, Definition.class); } catch (Exception ex) { throw bad("Не удалось прочитать правило"); } }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static DomainException conflict() { return new DomainException("conflict", "Настройки изменились. Обновите предложение"); }
    private static DomainException bad(String message) { return new DomainException("unprocessable", message); }
}
