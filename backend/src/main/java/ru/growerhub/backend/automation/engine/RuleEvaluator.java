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
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.plant.contract.PlantInfo;

@Component
public class RuleEvaluator {
    private final RuleSettings settings;
    public RuleEvaluator(RuleSettings settings) { this.settings = settings; }
    public record Context(Map<Integer, ResourceBinding> resources, Map<Integer, PlantInfo> plants, ZoneId timezone, Instant now) {}
    public record Decision(String key, boolean ready, String reason) {}
    private record Check(boolean valid, boolean value, String reason) {}

    public Set<Integer> validate(Definition d, Context context) {
        if (d == null || d.version() != 1 || d.name() == null || d.name().isBlank() || d.name().length() > 120
                || d.scopeId() == null || d.scopeType() == null || !Set.of("ROOM", "BOX").contains(d.scopeType())
                || d.cooldownSeconds() < settings.minCooldownSeconds() || d.cooldownSeconds() > settings.maxDelaySeconds()) throw bad("Проверьте название, область и паузу правила");
        var used = new TreeSet<Integer>(); var trigger = d.trigger();
        String type = type(trigger);
        switch (type) {
            case "schedule" -> { fields(trigger, "type", "at", "days"); time(trigger, "at"); days(trigger); }
            case "interval" -> {
                fields(trigger, "type", "seconds", "anchor"); bounded(trigger, "seconds", settings.minIntervalSeconds(), settings.maxDelaySeconds());
                try { Instant.parse(trigger.path("anchor").asText()); } catch (DateTimeException ex) { throw bad("Укажите начало интервала с часовым поясом UTC"); }
            }
            case "change" -> { fields(trigger, "type", "resourceId", "maxAgeSeconds"); resource(trigger, context, used); freshness(trigger); }
            default -> throw bad("Неизвестный способ запуска");
        }
        validateCondition(d.condition(), context, used, 0, new AtomicInteger());
        if (d.actions() == null || d.actions().isEmpty() || d.actions().size() > settings.actions()) throw bad("Последовательность должна содержать от 1 до " + settings.actions() + " действий");
        int delay = 0;
        for (var action : d.actions()) switch (type(action)) {
            case "set" -> {
                fields(action, "type", "resourceId", "value"); var r = resource(action, context, used);
                if (!Set.of("LIGHT_SWITCH", "EXHAUST_SWITCH", "AC_SWITCH").contains(r.role()) || r.zigbeeCoordinatorId() == null)
                    throw bad("Для этого устройства нужна отдельная команда ограниченного полива");
                String v = action.path("value").asText();
                if (!(v.equalsIgnoreCase(Objects.requireNonNullElse(r.onValue(), "ON")) || v.equalsIgnoreCase(Objects.requireNonNullElse(r.offValue(), "OFF"))))
                    throw bad("Значение не поддерживается этим каналом");
            }
            case "water" -> {
                fields(action, "type", "resourceId", "durationSeconds"); var r = resource(action, context, used);
                if (!"WATER_PUMP".equals(r.role())) throw bad("Для полива выберите назначенный поливной канал");
                bounded(action, "durationSeconds", 1, settings.maxWateringSeconds());
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
        String key = trigger(d.trigger(), context, runtime);
        if (key == null) return new Decision(null, false, "Ожидает следующего запуска");
        if (!condition.valid() || !condition.value()) return new Decision(key, false, condition.reason());
        if (runtime.has("lastStarted") && context.now().getEpochSecond() - runtime.get("lastStarted").asLong() < d.cooldownSeconds())
            return new Decision(key, false, "Пауза между запусками ещё не закончилась");
        return new Decision(key, true, "Условия выполнены");
    }
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
