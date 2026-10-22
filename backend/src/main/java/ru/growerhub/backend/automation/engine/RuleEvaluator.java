package ru.growerhub.backend.automation.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.automation.contract.AutomationData.ResourceBinding;
import ru.growerhub.backend.common.config.automation.RuleSettings;
import ru.growerhub.backend.common.config.automation.ControlSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.plant.contract.PlantInfo;

@Component
public class RuleEvaluator {
    private final RuleSettings settings;
    private final RuleSchedule schedule;
    private final ControlSettings controls;
    public RuleEvaluator(RuleSettings settings, ControlSettings controls) { this.settings = settings; this.schedule = new RuleSchedule(settings); this.controls = controls; }
    public record Context(Map<Integer, ResourceBinding> resources, Map<Integer, PlantInfo> plants, ZoneId timezone, Instant now) {}
    public record Decision(String key, boolean ready, String reason) {}
    public record ConditionDecision(boolean valid, boolean value, String reason) {}
    private record Check(boolean valid, boolean value, String reason) {}

    public Set<Integer> validate(Definition d, Context context) {
        if (d == null || !Set.of(1, 2, 3).contains(d.version()) || d.name() == null || d.name().isBlank() || d.name().length() > 120
                || d.scopeId() == null || d.scopeType() == null || !Set.of("ROOM", "BOX").contains(d.scopeType())
                || d.cooldownSeconds() < settings.minCooldownSeconds() || d.cooldownSeconds() > settings.maxDelaySeconds()) throw bad("Проверьте название, область и паузу правила");
        var used = new TreeSet<Integer>(); var trigger = d.trigger();
        String type = type(trigger);
        switch (type) {
            case "schedule", "interval", "calendar" -> schedule.validate(trigger, d.version());
            case "change" -> { fields(trigger, "type", "resourceId", "maxAgeSeconds"); resource(trigger, context, used); freshness(trigger); }
            case "repeat_while" -> {
                if (d.version() < 3) throw bad("Для повторяемого удержания используйте версию 3");
                fields(trigger, "type", "seconds", "first", "onConditionLoss");
                bounded(trigger, "seconds", settings.minIntervalSeconds(), settings.maxIntervalSeconds());
                if (!Set.of("immediate", "after_interval").contains(trigger.path("first").asText())
                        || !Set.of("release", "finish").contains(trigger.path("onConditionLoss").asText()) || d.condition() == null || d.condition().isNull())
                    throw bad("Укажите условие, первый запуск и поведение при его потере");
            }
            default -> throw bad("Неизвестный способ запуска");
        }
        validateCondition(d.condition(), context, used, 0, new AtomicInteger());
        if (d.actions() == null || d.actions().isEmpty() || d.actions().size() > settings.actions()) throw bad("Последовательность должна содержать от 1 до " + settings.actions() + " действий");
        if (d.version() >= 3 && (d.actions().stream().noneMatch(a -> "hold".equals(a.path("type").asText()))
                || d.actions().stream().anyMatch(a -> Set.of("set", "water").contains(a.path("type").asText()))))
            throw bad("Версия 3 предназначена для hold; прямые команды и ограниченный полив сохраняйте отдельным правилом версии 2");
        long delay = 0;
        for (var action : d.actions()) switch (type(action)) {
            case "hold" -> {
                if (d.version() < 3) throw bad("Для удержания используйте версию правила 3");
                fields(action, "type", "resourceId", "value", "durationSeconds", "priority");
                var r = resource(action, context, used);
                if (!Set.of("LIGHT_SWITCH", "EXHAUST_SWITCH", "AC_SWITCH").contains(r.role()) || r.zigbeeCoordinatorId() == null)
                    throw bad("Удержание доступно для света и климата; полив использует ограниченный сеанс");
                String value = action.path("value").asText();
                if (!(value.equalsIgnoreCase(Objects.requireNonNullElse(r.onValue(), "ON")) || value.equalsIgnoreCase(Objects.requireNonNullElse(r.offValue(), "OFF")) || Set.of("ON", "OFF").contains(value.toUpperCase(Locale.ROOT)))) throw bad("Значение не поддерживается этим каналом");
                delay += bounded(action, "durationSeconds", controls.minTtlSeconds(), controls.maxTtlSeconds());
                bounded(action, "priority", controls.minRulePriority(), controls.maxRulePriority());
            }
            case "set" -> {
                fields(action, "type", "resourceId", "value"); var r = resource(action, context, used);
                if (!Set.of("LIGHT_SWITCH", "EXHAUST_SWITCH", "AC_SWITCH").contains(r.role()) || r.zigbeeCoordinatorId() == null)
                    throw bad("Для этого устройства нужна отдельная команда ограниченного полива");
                String v = action.path("value").asText();
                if (!(v.equalsIgnoreCase(Objects.requireNonNullElse(r.onValue(), "ON")) || v.equalsIgnoreCase(Objects.requireNonNullElse(r.offValue(), "OFF")) || Set.of("ON", "OFF").contains(v.toUpperCase(Locale.ROOT))))
                    throw bad("Значение не поддерживается этим каналом");
            }
            case "water" -> {
                if (d.version() >= 2) fields(action, "type", "resourceId", "durationSeconds", "pulse");
                else fields(action, "type", "resourceId", "durationSeconds");
                var r = resource(action, context, used);
                if (!"WATER_PUMP".equals(r.role())) throw bad("Для полива выберите назначенный поливной канал");
                boolean pulse = action.hasNonNull("pulse");
                int seconds = bounded(action, "durationSeconds", 1, pulse ? settings.maxTotalWateringSeconds() : settings.maxWateringSeconds());
                if (pulse) {
                    var p = action.get("pulse"); if (!p.isObject()) throw bad("Укажите длительность импульса и паузы");
                    fields(p, "runSeconds", "pauseSeconds");
                    int run = bounded(p, "runSeconds", 1, settings.maxWateringSeconds());
                    int pause = bounded(p, "pauseSeconds", 1, settings.maxDelaySeconds());
                    delay += ((seconds + (long) run - 1) / run - 1) * pause;
                }
                if (d.version() >= 2) delay += seconds;
            }
            case "delay" -> { fields(action, "type", "seconds"); delay += bounded(action, "seconds", 1, settings.maxDelaySeconds()); }
            case "notify" -> {
                fields(action, "type", "text");
                if (!action.path("text").isTextual() || action.get("text").asText().isBlank() || action.get("text").asText().length() > 1000) throw bad("Напишите короткое уведомление");
            }
            default -> throw bad("Это действие не поддерживается");
        }
        if (delay > settings.maxDelaySeconds()) throw bad("Слишком длинная последовательность");
        return used;
    }
    private void validateCondition(JsonNode node, Context context, Set<Integer> used, int depth, AtomicInteger nodes) {
        if (node == null || node.isNull()) return;
        if (depth > settings.conditionDepth() || nodes.incrementAndGet() > settings.conditionNodes()) throw bad("Слишком сложные условия");
        switch (type(node)) {
            case "all", "any" -> {
                fields(node, "type", "conditions");
                if (!node.path("conditions").isArray() || node.get("conditions").isEmpty()) throw bad("Добавьте условия");
                for (var child : node.get("conditions")) validateCondition(child, context, used, depth + 1, nodes);
            }
            case "not" -> { fields(node, "type", "condition"); if (!node.hasNonNull("condition")) throw bad("Добавьте условие отрицания"); validateCondition(node.get("condition"), context, used, depth + 1, nodes); }
            case "time" -> { fields(node, "type", "from", "until", "days"); time(node, "from"); time(node, "until"); days(node); }
            case "stage" -> {
                fields(node, "type", "plantId", "stage");
                if (!context.plants().containsKey(integer(node, "plantId")) || !node.path("stage").isTextual() || node.get("stage").asText().isBlank()) throw bad("Выберите своё растение и стадию");
            }
            case "sensor" -> {
                fields(node, "type", "resourceId", "op", "value", "hysteresis", "holdSeconds", "maxAgeSeconds");
                resource(node, context, used); freshness(node);
                String op = node.path("op").asText();
                if (!Set.of("gt", "gte", "lt", "lte", "eq", "ne").contains(op)) throw bad("Неизвестное сравнение показаний");
                if (!node.hasNonNull("value") || !node.get("value").isValueNode()) throw bad("Укажите сравниваемое значение");
                if (!Set.of("eq", "ne").contains(op) && !finite(node.get("value"))) throw bad("Порог должен быть числом");
                if (node.has("hysteresis") && (!finite(node.get("hysteresis")) || node.get("hysteresis").asDouble() < 0 || Set.of("eq", "ne").contains(op))) throw bad("Гистерезис доступен только для числовых порогов");
                if (node.has("holdSeconds")) bounded(node, "holdSeconds", 0, settings.maxDelaySeconds());
            }
            default -> throw bad("Неизвестное условие");
        }
    }
    public Decision evaluate(Definition d, Context context, ObjectNode runtime) {
        if (!runtime.path("conditions").isObject()) runtime.putObject("conditions");
        Check condition = condition(d.condition(), context, (ObjectNode) runtime.get("conditions"), "root");
        String key;
        if (type(d.trigger()).equals("repeat_while")) {
            if (!condition.valid() || !condition.value()) {
                runtime.put("repeatActive", false); runtime.remove("repeatAnchor");
                return new Decision(null, false, condition.reason());
            }
            if (!runtime.path("repeatActive").asBoolean()) {
                runtime.put("repeatActive", true); runtime.put("repeatAnchor", context.now().getEpochSecond());
                runtime.put("repeatGeneration", runtime.path("repeatGeneration").asLong() + 1);
            }
            long elapsed = context.now().getEpochSecond() - runtime.path("repeatAnchor").asLong();
            int seconds = d.trigger().get("seconds").asInt(); long slot = Math.floorDiv(elapsed, seconds);
            if (elapsed < 0 || slot == 0 && "after_interval".equals(d.trigger().get("first").asText())
                    || elapsed % seconds > settings.scheduleGraceSeconds()) return new Decision(null, false, "Ожидает следующего периода, пропущенные циклы не догоняются");
            key = "repeat:" + runtime.path("repeatGeneration").asLong() + ":" + slot;
        } else if (d.version() >= 2 && !type(d.trigger()).equals("change")) {
            var occurrence = schedule.current(d.trigger(), d.version(), context.now(), context.timezone());
            key = occurrence == null ? null : occurrence.key();
        } else key = trigger(d.trigger(), context, runtime);
        if (key == null) return new Decision(null, false, "Ожидает следующего запуска");
        if (!condition.valid() || !condition.value()) return new Decision(key, false, condition.reason());
        if (runtime.has("lastStarted") && context.now().getEpochSecond() - runtime.get("lastStarted").asLong() < d.cooldownSeconds())
            return new Decision(key, false, "Пауза между запусками ещё не закончилась");
        return new Decision(key, true, "Условия выполнены");
    }

    public ConditionDecision conditions(Definition d, Context context, ObjectNode runtime) {
        if (!runtime.path("conditions").isObject()) runtime.putObject("conditions");
        var check = condition(d.condition(), context, (ObjectNode) runtime.get("conditions"), "root");
        return new ConditionDecision(check.valid(), check.value(), check.reason());
    }

    public Timing timing(Definition d, Context context) {
        List<TimelineStep> timeline = new ArrayList<>(); long offset = 0; int active = 0;
        for (int i = 0; i < d.actions().size(); i++) {
            var action = d.actions().get(i); String type = type(action); Integer id = action.has("resourceId") ? action.get("resourceId").asInt() : null;
            if (type.equals("water")) {
                int total = action.get("durationSeconds").asInt(); active += total;
                int run = action.hasNonNull("pulse") ? action.get("pulse").get("runSeconds").asInt() : total;
                int pause = action.hasNonNull("pulse") ? action.get("pulse").get("pauseSeconds").asInt() : 0;
                int remaining = total;
                while (remaining > 0) {
                    int seconds = Math.min(run, remaining);
                    if (timeline.size() < settings.previewTimelineSteps()) timeline.add(new TimelineStep(i, "water", offset, seconds, id, "Полив"));
                    offset += seconds; remaining -= seconds;
                    if (remaining > 0) {
                        if (timeline.size() < settings.previewTimelineSteps()) timeline.add(new TimelineStep(i, "delay", offset, pause, id, "Пауза после подтверждённой остановки насоса"));
                        offset += pause;
                    }
                }
            } else {
                int seconds = type.equals("delay") ? action.get("seconds").asInt() : type.equals("hold") ? action.get("durationSeconds").asInt() : 0;
                if (timeline.size() < settings.previewTimelineSteps()) timeline.add(new TimelineStep(i, type, offset, seconds, id, type.equals("delay") ? "Пауза" : type.equals("notify") ? action.get("text").asText() : "Установить " + action.get("value").asText()));
                offset += seconds;
            }
        }
        List<String> notes = new ArrayList<>();
        notes.add("device_ack_delay");
        if (type(d.trigger()).equals("repeat_while")) { notes.add("repeat_while_no_catchup"); notes.add("priority_arbitration"); }
        else if (d.version() >= 2 && !type(d.trigger()).equals("change")) notes.add(d.trigger().path("misfire").asText("skip").equals("run_once")
                ? "misfire_run_once" : "misfire_skip");
        if (timeline.size() == settings.previewTimelineSteps()) notes.add("timeline_truncated");
        if (d.version() == 1 && d.actions().stream().anyMatch(a -> type(a).equals("water"))) notes.add("legacy_nonblocking_water");
        String zone = d.trigger().path("timezone").asText(context.timezone().getId());
        return new Timing(zone, schedule.next(d.trigger(), d.version(), context.now(), context.timezone(), settings.previewRuns()), List.copyOf(timeline), active, offset, List.copyOf(notes), settings.scheduleGraceSeconds(), settings.previewTimelineSteps());
    }

    public Map<String, Object> schema() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 3); result.put("supportedVersions", List.of(1, 2, 3));
        result.put("definition", Map.of("version", 3, "name", "строка до 120 символов", "scopeType", "ROOM|BOX", "scopeId", "ID из gh_farm_report",
                "trigger", "один объект из triggers", "condition", "null либо объект из conditions", "actions", "непустой массив из actions", "cooldownSeconds", range(settings.minCooldownSeconds(), settings.maxDelaySeconds())));
        result.put("triggers", List.of(
                Map.of("type", "schedule", "at", "HH:mm", "days?", List.of(1, 2, 3, 4, 5, 6, 7), "misfire?", "skip|run_once"),
                Map.of("type", "interval", "seconds", range(settings.minIntervalSeconds(), settings.maxIntervalSeconds()), "anchor", "ISO UTC первого запуска", "misfire?", "skip|run_once"),
                Map.of("type", "calendar", "every", range(1, settings.maxIntervalSeconds() / 86400), "unit", "day|week (для week максимум " + settings.maxIntervalSeconds() / 604800 + ")", "startDate", "YYYY-MM-DD", "at", "HH:mm", "timezone?", "IANA, по умолчанию пояс аккаунта", "misfire?", "skip|run_once"),
                Map.of("type", "change", "resourceId", "ID своего датчика", "maxAgeSeconds", range(1, settings.maxFreshnessSeconds())),
                Map.of("type", "repeat_while", "seconds", range(settings.minIntervalSeconds(), settings.maxIntervalSeconds()), "first", "immediate|after_interval", "onConditionLoss", "release|finish", "condition", "обязательно: фактические свежие показания, не желаемое состояние")));
        result.put("conditions", List.of("{type:all|any,conditions:[...]}", "{type:not,condition:...}", "{type:time,from:HH:mm,until:HH:mm,days?:[1..7]}", "{type:stage,plantId,stage}",
                "{type:sensor,resourceId,op:gt|gte|lt|lte|eq|ne,value,hysteresis?:number,holdSeconds?:0.." + settings.maxDelaySeconds() + ",maxAgeSeconds:1.." + settings.maxFreshnessSeconds() + "}"));
        result.put("actions", List.of(Map.of("type", "set", "resourceId", "ID своего канала света или климата", "value", "on/off из отчёта устройства"),
                Map.of("type", "hold", "resourceId", "ID LIGHT_SWITCH|EXHAUST_SWITCH|AC_SWITCH", "value", "on/off из отчёта", "durationSeconds", range(controls.minTtlSeconds(), controls.maxTtlSeconds()), "priority", range(controls.minRulePriority(), controls.maxRulePriority()), "version", 3),
                Map.of("type", "water", "resourceId", "ID WATER_PUMP", "durationSeconds", range(1, settings.maxTotalWateringSeconds()), "pulse?", Map.of("runSeconds", range(1, settings.maxWateringSeconds()), "pauseSeconds", range(1, settings.maxDelaySeconds())), "constraints", "Без pulse durationSeconds не более " + settings.maxWateringSeconds() + ". С pulse это общая работа насоса, паузы не входят."),
                Map.of("type", "delay", "seconds", range(1, settings.maxDelaySeconds())), Map.of("type", "notify", "text", "строка до 1000 символов")));
        result.put("limits", Map.of("maxActions", settings.actions(), "maxCycleSeconds", settings.maxDelaySeconds(), "maxDailyWateringSecondsPerChannel", settings.maxDailyWateringSeconds(), "scheduleGraceSeconds", settings.scheduleGraceSeconds()));
        result.put("instructions", "Для совместного управления светом/климатом создавай version=3 с hold. hold удерживает значение с priority только durationSeconds от фактического подтверждения. По окончании снимается только требование: никогда не добавляй OFF/восстановление прошлого состояния для освобождения канала. Более высокий priority выигрывает; равные — более раннее неизменное время создания, затем ключ источника. Готовые сценарии имеют priority=" + controls.scenarioPriority() + ", ручные=" + controls.manualPriority() + ". Для периодического удержания пока фактически включён свет: repeat_while, seconds=период, first=immediate|after_interval, onConditionLoss=release|finish, condition sensor eq onValue с maxAgeSeconds. release сразу снимает требование при OFF; finish даёт закончить начатое удержание. Устаревшие данные всегда снимают требование. Пропущенные периоды не догоняются. Остальные сценарии, особенно water, могут использовать version=2 и сохраняют эксклюзивность. calendar — каждые N дней в местное время, interval — точные секунды, misfire=skip по умолчанию. Полив pulse — один ограниченный сеанс pump, никогда не заменяй его set/hold. gh_prepare_change kind=rule с payload {enabled,definition}; подготовка только показывает карточку и ничего не включает. Для ручного set укажи ttlSeconds: это временное вмешательство, не отключение автоматики навсегда.");
        return Collections.unmodifiableMap(result);
    }
    private Map<String, Integer> range(int min, int max) { return Map.of("minimum", min, "maximum", max); }
    private String trigger(JsonNode trigger, Context c, ObjectNode state) {
        var local = c.now().atZone(c.timezone());
        return switch (type(trigger)) {
            case "schedule" -> days(trigger).contains(local.getDayOfWeek().getValue()) && local.toLocalTime().withSecond(0).withNano(0).equals(time(trigger, "at"))
                    ? "schedule:" + local.toLocalDate() + ":" + trigger.get("at").asText() : null;
            case "interval" -> {
                long elapsed = Duration.between(Instant.parse(trigger.get("anchor").asText()), c.now()).getSeconds();
                yield elapsed < 0 ? null : "interval:" + Math.floorDiv(elapsed, trigger.get("seconds").asLong());
            }
            case "change" -> {
                var r = c.resources().get(integer(trigger, "resourceId"));
                if (!fresh(r, c, freshness(trigger))) yield null;
                String value = Objects.toString(r.currentValue(), "");
                if (state.has("lastValue") && !state.get("lastValue").asText().equals(value)) {
                    state.put("pendingChange", "change:" + r.lastSeenAt()); state.put("changeAt", c.now().getEpochSecond());
                }
                state.put("lastValue", value);
                yield state.has("pendingChange") && c.now().getEpochSecond() - state.path("changeAt").asLong() <= settings.maxFreshnessSeconds()
                        ? state.get("pendingChange").asText() : null;
            }
            default -> null;
        };
    }
    private Check condition(JsonNode node, Context c, ObjectNode state, String path) {
        if (node == null || node.isNull()) return new Check(true, true, "Условий нет");
        switch (type(node)) {
            case "all", "any": {
                var checks = new ArrayList<Check>(); int index = 0;
                for (var child : node.get("conditions")) checks.add(condition(child, c, state, path + "." + index++));
                if (checks.stream().anyMatch(v -> !v.valid())) return checks.stream().filter(v -> !v.valid()).findFirst().orElseThrow();
                boolean value = type(node).equals("all") ? checks.stream().allMatch(Check::value) : checks.stream().anyMatch(Check::value);
                return new Check(true, value, value ? "Условия выполнены" : "Условия пока не выполнены");
            }
            case "not": { var child = condition(node.get("condition"), c, state, path + ".not"); return new Check(child.valid(), child.valid() && !child.value(), child.valid() ? "Проверено обратное условие" : child.reason()); }
            case "time": {
                var local = c.now().atZone(c.timezone()); var from = time(node, "from"); var until = time(node, "until"); var current = local.toLocalTime();
                boolean overnight = from.isAfter(until), window = from.equals(until) || (overnight ? !current.isBefore(from) || current.isBefore(until) : !current.isBefore(from) && current.isBefore(until));
                var day = overnight && current.isBefore(until) ? local.minusDays(1).getDayOfWeek() : local.getDayOfWeek();
                return new Check(true, window && days(node).contains(day.getValue()), "Проверено разрешённое время");
            }
            case "stage": {
                var plant = c.plants().get(integer(node, "plantId"));
                return new Check(plant != null, plant != null && node.get("stage").asText().equals(plant.growthStage()), "Проверена стадия растения");
            }
            case "sensor": {
                var r = c.resources().get(integer(node, "resourceId"));
                if (!state.path(path).isObject()) state.putObject(path); var memory = (ObjectNode) state.get(path);
                if (!fresh(r, c, freshness(node))) { memory.removeAll(); return new Check(false, false, "Нет свежих показаний датчика"); }
                String op = node.get("op").asText(); boolean value;
                if (Set.of("eq", "ne").contains(op)) {
                    value = Objects.toString(r.currentValue()).equalsIgnoreCase(node.get("value").asText());
                    if (op.equals("ne")) value = !value;
                } else {
                    Double measured;
                    try { measured = Double.valueOf(Objects.toString(r.currentValue())); } catch (NumberFormatException ex) { measured = null; }
                    if (measured == null || !Double.isFinite(measured)) { memory.removeAll(); return new Check(false, false, "Датчик не передал число"); }
                    double threshold = node.get("value").asDouble(), h = node.path("hysteresis").asDouble(0);
                    boolean latched = memory.path("latched").asBoolean(false);
                    value = switch (op) {
                        case "gt" -> latched ? measured > threshold - h : measured > threshold;
                        case "gte" -> latched ? measured >= threshold - h : measured >= threshold;
                        case "lt" -> latched ? measured < threshold + h : measured < threshold;
                        default -> latched ? measured <= threshold + h : measured <= threshold;
                    };
                }
                memory.put("latched", value);
                if (!value) { memory.remove("since"); return new Check(true, false, "Показание не достигло выбранного условия"); }
                if (!memory.has("since")) memory.put("since", c.now().getEpochSecond());
                boolean held = c.now().getEpochSecond() - memory.get("since").asLong() >= node.path("holdSeconds").asInt(0);
                return new Check(true, held, held ? "Показания устойчивы" : "Ожидает устойчивых показаний");
            }
            default: return new Check(false, false, "Условие недоступно");
        }
    }
    public boolean fresh(ResourceBinding r, Context c, int seconds) {
        return r != null && r.ready() && r.lastSeenAt() != null && r.currentValue() != null
                && !r.lastSeenAt().isBefore(LocalDateTime.ofInstant(c.now().minusSeconds(seconds), ZoneOffset.UTC))
                && !r.lastSeenAt().isAfter(LocalDateTime.ofInstant(c.now(), ZoneOffset.UTC));
    }
    private ResourceBinding resource(JsonNode node, Context context, Set<Integer> used) {
        int id = integer(node, "resourceId"); var resource = context.resources().get(id);
        if (resource == null) throw bad("Выбранное устройство не найдено в вашей ферме"); used.add(id); return resource;
    }
    private int freshness(JsonNode node) { return bounded(node, "maxAgeSeconds", 1, settings.maxFreshnessSeconds()); }
    private static boolean finite(JsonNode value) { return value != null && value.isNumber() && Double.isFinite(value.asDouble()); }
    private static int integer(JsonNode node, String key) { if (!node.path(key).isIntegralNumber() || !node.get(key).canConvertToInt()) throw bad("Укажите целое число: " + key); return node.get(key).intValue(); }
    private static int bounded(JsonNode node, String key, int min, int max) { int value = integer(node, key); if (value < min || value > max) throw bad(key + ": допустимо от " + min + " до " + max); return value; }
    private static String type(JsonNode node) { if (node == null || !node.isObject() || !node.path("type").isTextual()) throw bad("Укажите тип условия или действия"); return node.get("type").asText(); }
    private static LocalTime time(JsonNode node, String key) { try { String text = node.path(key).asText(); if (!text.matches("\\d{2}:\\d{2}")) throw new DateTimeException("time"); return LocalTime.parse(text); } catch (DateTimeException ex) { throw bad("Укажите время ЧЧ:ММ"); } }
    private static Set<Integer> days(JsonNode node) {
        if (!node.has("days")) return Set.of(1, 2, 3, 4, 5, 6, 7);
        if (!node.get("days").isArray() || node.get("days").isEmpty()) throw bad("Выберите дни недели");
        var days = new HashSet<Integer>(); for (var day : node.get("days")) { if (!day.isIntegralNumber() || day.asInt() < 1 || day.asInt() > 7) throw bad("День недели: от 1 до 7"); days.add(day.asInt()); } return days;
    }
    private static void fields(JsonNode node, String... allowed) { Set<String> keys = Set.of(allowed); node.fieldNames().forEachRemaining(key -> { if (!keys.contains(key)) throw bad("Неизвестное поле правила: " + key); }); }
    private static DomainException bad(String message) { return new DomainException("unprocessable", message); }
}
