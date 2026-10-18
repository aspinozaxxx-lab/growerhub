package ru.growerhub.backend.advisor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.RuleData;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.user.UserFacade;

@Service
public class AssistantTools {
    private final AutomationFacade automation;
    private final PlantFacade plants;
    private final JournalFacade journal;
    private final UserFacade users;
    private final AssistantIdentity identity;
    private final AssistantSettings settings;
    private final ObjectMapper json;
    private final Clock clock;

    public AssistantTools(AutomationFacade automation, PlantFacade plants, JournalFacade journal, UserFacade users,
            AssistantIdentity identity, AssistantSettings settings, ObjectMapper json, Clock clock) {
        this.automation = automation; this.plants = plants; this.journal = journal; this.users = users;
        this.identity = identity; this.settings = settings; this.json = json; this.clock = clock;
    }
    public record Prepared(String title, JsonNode payload, String fingerprint) {}

    public List<Map<String, Object>> catalog() {
        return List.of(
            tool("gh_farm_report", "Текущее состояние собственной фермы, устройства, показания, сценарии и растения. Числа рассчитывает сервер.", Map.of(), List.of()),
            tool("gh_plant_details", "История ухода и сведения о своём растении", Map.of("plantId", integer()), List.of("plantId")),
            tool("gh_resource_history", "История и энергопотребление своей розетки. hours — число часов", Map.of("resourceId", integer(), "hours", integer()), List.of("resourceId", "hours")),
            tool("gh_rule_details", "Свои правила, история и схема языка правил. ruleId для подробностей своего правила. Для настроек готового сценария передай scopeType=ROOM|BOX и scopeId.", Map.of("ruleId", Map.of("type", "string"), "scopeType", Map.of("type", "string"), "scopeId", integer()), List.of()),
            tool("gh_validate_rule", "Проверить правило, конфликты каналов и готовность устройств без сохранения. payload — определение version=1 из gh_rule_details.", Map.of("payload", Map.of("type", "object")), List.of("payload")),
            tool("gh_prepare_change", "Подготовить предложение, НЕ применяя его. kind=care: payload {plantId,action,text,eventAt}; reminder: {plantId,action,title,dueAt,repeatDays,repeatMode}; scenario: {scopeType:ROOM|BOX,scopeId,scenarioType,enabled,config}; rule: {ruleId?:UUID,revision?:number,enabled:boolean,definition:object из gh_rule_details}; device: {type:set|water,resourceId,value?:string,durationSeconds?:integer}. Даты ISO UTC. Человек подтверждает кнопкой.",
                Map.of("kind", Map.of("type", "string", "enum", List.of("care", "reminder", "scenario", "rule", "device")), "payload", Map.of("type", "object")), List.of("kind", "payload")));
    }

    public JsonNode read(String name, JsonNode input, AuthenticatedUser user) {
        return switch (name) {
            case "gh_farm_report" -> report(user);
            case "gh_plant_details" -> {
                int id = id(input, "plantId"); var plant = plants.requireOwnedPlantInfo(id, user);
                yield json.valueToTree(Map.of("plant", plant, "care", journal.searchCare(user, id, null, null, null, null, 0),
                        "reminders", journal.careReminders(user).stream().filter(r -> r.plantId().equals(id)).toList()));
            }
            case "gh_resource_history" -> {
                int resource = id(input, "resourceId");
                if (automation.getFarmsOverview(user).farms().stream().flatMap(f -> java.util.stream.Stream.concat(f.slots().stream(), f.greenhouses().stream().flatMap(g -> g.slots().stream())))
                        .noneMatch(r -> r.id().equals(resource))) throw bad("Устройство не найдено в вашей ферме");
                int hours = id(input, "hours");
                if (hours > settings.retentionDays() * 24) throw bad("Слишком длинный период истории");
                yield json.valueToTree(automation.getResourceStatistics(user, id(input, "resourceId"), hours));
            }
            case "gh_rule_details" -> {
                if (input.hasNonNull("scopeId")) yield json.valueToTree(scenarioSource(input, user));
                if (input.hasNonNull("ruleId")) { UUID rule = uuid(input, "ruleId"); yield json.valueToTree(Map.of("rule", automation.getRule(rule, user), "history", automation.ruleHistory(rule, user))); }
                yield json.valueToTree(Map.of("rules", automation.listRules(user), "schema", ruleSchema()));
            }
            case "gh_validate_rule" -> json.valueToTree(automation.previewRule(null, ruleDefinition(input.get("payload")), user));
            default -> throw bad("Такого инструмента у помощника нет");
        };
    }

    public JsonNode report(AuthenticatedUser user) {
        var overview = automation.getFarmsOverview(user);
        List<Map<String, Object>> farms = overview.farms().stream().map(farm -> {
            var row = new LinkedHashMap<String, Object>(); row.put("id", farm.id()); row.put("name", farm.name());
            row.put("enabled", farm.enabled()); row.put("resources", farm.slots().stream().map(this::compactResource).toList()); row.put("scenarios", farm.scenarios().stream().map(this::compactScenario).toList());
            row.put("greenhouses", farm.greenhouses().stream().map(zone -> {
                var z = new LinkedHashMap<String, Object>(); z.put("id", zone.id()); z.put("name", zone.name()); z.put("enabled", zone.enabled());
                z.put("resources", zone.slots().stream().map(this::compactResource).toList()); z.put("scenarios", zone.scenarios().stream().map(this::compactScenario).toList());
                z.put("plants", zone.plants().stream().map(p -> Map.of("id", p.id(), "name", p.name())).toList()); return z;
            }).toList()); return (Map<String, Object>) row;
        }).toList();
        var resources = overview.farms().stream().flatMap(f -> java.util.stream.Stream.concat(f.slots().stream(), f.greenhouses().stream().flatMap(g -> g.slots().stream()))).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("at", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)); result.put("timezone", users.getTimezone(user.id()));
        result.put("demo", user.isDemo()); result.put("farms", farms); result.put("plantCount", plants.listPlants(user).size());
        result.put("farmCount", farms.size()); result.put("greenhouseCount", overview.farms().stream().mapToInt(f -> f.greenhouses().size()).sum());
        result.put("resourceCount", resources.size()); result.put("notReadyCount", resources.stream().filter(r -> !r.ready()).count());
        result.put("plants", plants.listPlants(user).stream().map(p -> {
            var row = new LinkedHashMap<String, Object>(); row.put("id", p.id()); row.put("name", p.name()); row.put("stage", p.growthStage());
            row.put("type", p.plantType()); row.put("strain", p.strain()); row.put("location", p.locationLabel()); return row;
        }).toList());
        result.put("reminders", journal.careReminders(user)); return json.valueToTree(result);
    }
    private Map<String, Object> compactResource(ResourceBinding r) {
        var value = new LinkedHashMap<String, Object>(); value.put("id", r.id()); value.put("role", r.role()); value.put("name", r.label());
        value.put("value", r.currentValue()); value.put("at", r.lastSeenAt()); value.put("ready", r.ready());
        if (!r.ready()) value.put("reason", r.reason()); if (r.onValue() != null) { value.put("on", r.onValue()); value.put("off", r.offValue()); }
        return value;
    }
    private Map<String, Object> compactScenario(ScenarioConfig s) {
        var value = new LinkedHashMap<String, Object>(); value.put("type", s.scenarioType()); value.put("enabled", s.enabled()); value.put("readiness", s.readiness()); return value;
    }

    public Prepared prepare(String kind, JsonNode payload, AuthenticatedUser user) {
        if (payload == null || !payload.isObject() || payload.toString().length() > settings.maxToolResultChars()) throw bad("Неверное предложение");
        return switch (kind) {
            case "care" -> {
                int id = id(payload, "plantId"); var plant = plants.requireOwnedPlantInfo(id, user);
                var command = care(payload, "assistant:preview"); journal.validateCareEntry(id, user, command);
                yield new Prepared("Записать уход: " + plant.name(), payload, fingerprint(plant));
            }
            case "reminder" -> {
                var command = reminder(payload); var plant = plants.requireOwnedPlantInfo(command.plantId(), user);
                journal.validateCareReminder(user, command);
                yield new Prepared("Напомнить: " + command.title() + " — " + plant.name(), payload, fingerprint(plant));
            }
            case "scenario" -> {
                scenarioSource(payload, user);
                String type = text(payload, "scenarioType");
                if (!Set.of("ROOM_CLIMATE", "BOX_CLIMATE", "LIGHT_SCHEDULE", "WATERING").contains(type)
                        || !payload.path("enabled").isBoolean() || !payload.path("config").isObject()) throw bad("Проверьте параметры сценария");
                @SuppressWarnings("unchecked") Map<String, Object> config = json.convertValue(payload.get("config"), Map.class);
                automation.previewScenario(user, text(payload, "scopeType"), id(payload, "scopeId"), new ScenarioConfigRequest(type, payload.get("enabled").asBoolean(), config));
                yield new Prepared("Изменить сценарий «" + type + "»", payload, automation.scenarioFingerprint(text(payload, "scopeType"), id(payload, "scopeId"), user));
            }
            case "rule" -> {
                UUID ruleId = payload.hasNonNull("ruleId") ? uuid(payload, "ruleId") : null;
                var d = ruleDefinition(payload.get("definition"));
                var check = automation.previewRule(ruleId, d, user);
                if (!payload.path("enabled").isBoolean()) throw bad("Укажите, включать ли правило");
                if (payload.path("enabled").asBoolean() && (!check.conflicts().isEmpty() || !check.readiness().isEmpty()))
                    throw bad(String.join(". ", java.util.stream.Stream.concat(check.conflicts().stream(), check.readiness().stream()).toList()));
                if (ruleId != null && automation.getRule(ruleId, user).revision() != id(payload, "revision")) throw new DomainException("conflict", "Версия правила изменилась");
                var shown = payload.deepCopy();
                var names = new LinkedHashMap<String, String>();
                var overview = automation.getFarmsOverview(user);
                overview.farms().stream().flatMap(f -> java.util.stream.Stream.concat(f.slots().stream(), f.greenhouses().stream().flatMap(g -> g.slots().stream())))
                        .filter(r -> check.resources().contains(r.id())).forEach(r -> names.put(r.id().toString(), r.label()));
                ((com.fasterxml.jackson.databind.node.ObjectNode) shown).set("resourceNames", json.valueToTree(names));
                String scopeName = "ROOM".equals(d.scopeType()) ? overview.farms().stream().filter(f -> f.id().equals(d.scopeId())).map(f -> f.name()).findFirst().orElse("")
                        : overview.farms().stream().flatMap(f -> f.greenhouses().stream()).filter(g -> g.id().equals(d.scopeId())).map(g -> g.name()).findFirst().orElse("");
                ((com.fasterxml.jackson.databind.node.ObjectNode) shown).put("scopeName", scopeName);
                yield new Prepared("Сценарий «" + d.name() + "»: " + check.description(), shown, fingerprint(List.of(automation.ruleFingerprint(d, user), ruleId == null ? "new" : automation.getRule(ruleId, user).revision())));
            }
            case "device" -> new Prepared("Команда устройству: " + automation.previewDeviceCommand(payload, user).path("device").asText(), payload, fingerprint(automation.previewDeviceCommand(payload, user)));
            default -> throw bad("Этот вид изменения недоступен");
        };
    }

    public JsonNode apply(String kind, JsonNode payload, String expected, UUID proposalId, AuthenticatedUser user) {
        if (user.isDemo()) users.lockDemoOwner(user.id()); else users.lockAccount(user.id());
        var prepared = prepare(kind, payload, user);
        if (!prepared.fingerprint().equals(expected)) throw new DomainException("conflict", "Настройки изменились. Попросите помощника подготовить новое предложение");
        return switch (kind) {
            case "care" -> json.valueToTree(journal.createCareEntry(id(payload, "plantId"), user, care(payload, "assistant:" + proposalId)));
            case "reminder" -> json.valueToTree(journal.saveCareReminder(null, user, reminder(payload)));
            case "scenario" -> {
                List<ScenarioConfig> current = scenarioSource(payload, user);
                var commands = new ArrayList<ScenarioConfigRequest>();
                String type = text(payload, "scenarioType");
                current.stream().filter(s -> !s.scenarioType().equals(type)).forEach(s -> commands.add(new ScenarioConfigRequest(s.scenarioType(), s.enabled(), s.config())));
                @SuppressWarnings("unchecked") Map<String, Object> config = json.convertValue(payload.get("config"), Map.class);
                commands.add(new ScenarioConfigRequest(type, payload.get("enabled").booleanValue(), config));
                var request = new SaveScenariosRequest(commands);
                if ("ROOM".equals(text(payload, "scopeType"))) automation.replaceUserFarmScenarios(user, id(payload, "scopeId"), request);
                else automation.replaceGreenhouseScenarios(user, id(payload, "scopeId"), request);
                yield json.valueToTree(Map.of("saved", true, "starts", payload.get("enabled").booleanValue() ? "next_evaluation" : "disabled"));
            }
            case "rule" -> json.valueToTree(automation.saveRule(payload.hasNonNull("ruleId") ? uuid(payload, "ruleId") : null,
                    new RuleData.Save(payload.hasNonNull("revision") ? id(payload, "revision") : null, payload.path("enabled").asBoolean(), ruleDefinition(payload.get("definition"))), user));
            case "device" -> automation.applyDeviceCommand(payload, user);
            default -> throw bad("Неподдерживаемое изменение");
        };
    }
    private UUID uuid(JsonNode node, String field) { try { return UUID.fromString(text(node, field)); } catch (IllegalArgumentException ex) { throw bad("Неверный идентификатор правила"); } }
    private RuleData.Definition ruleDefinition(JsonNode node) { try { return json.treeToValue(node, RuleData.Definition.class); } catch (Exception ex) { throw bad("Проверьте структуру правила"); } }
    private Map<String, Object> ruleSchema() {
        return Map.of("definition", "{version:1,name,scopeType:ROOM|BOX,scopeId,trigger,condition:null|object,actions:[...],cooldownSeconds:60..86400}",
                "triggers", List.of("{type:schedule,at:HH:mm,days?:[1..7]}", "{type:interval,seconds:60..86400,anchor:ISO_UTC}", "{type:change,resourceId,maxAgeSeconds:1..900}"),
                "conditions", List.of("{type:all|any,conditions:[...]}", "{type:not,condition:...}", "{type:time,from:HH:mm,until:HH:mm,days?:[1..7]}", "{type:stage,plantId,stage}", "{type:sensor,resourceId,op:gt|gte|lt|lte|eq|ne,value,hysteresis?:number,holdSeconds?:0..86400,maxAgeSeconds:1..900}"),
                "actions", List.of("{type:set,resourceId,value:ON|OFF или on_value/off_value устройства}", "{type:water,resourceId,durationSeconds:1..300}", "{type:delay,seconds:1..86400}", "{type:notify,text}"),
                "limits", "До 16 действий; паузы суммарно до суток. Сначала проверь gh_validate_rule. Для света и климата set, для насоса только water. Конфликтующее правило надо сначала отключить явно.");
    }

    private List<ScenarioConfig> scenarioSource(JsonNode payload, AuthenticatedUser user) {
        var overview = automation.getFarmsOverview(user); int id = id(payload, "scopeId");
        return switch (text(payload, "scopeType")) {
            case "ROOM" -> overview.farms().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(() -> bad("Ферма не найдена")).scenarios();
            case "BOX" -> overview.farms().stream().flatMap(f -> f.greenhouses().stream()).filter(g -> g.id().equals(id)).findFirst().orElseThrow(() -> bad("Теплица не найдена")).scenarios();
            default -> throw bad("Выберите ферму или теплицу");
        };
    }
    private CareData.EntryCommand care(JsonNode p, String key) { return new CareData.EntryCommand(text(p, "action"), p.path("text").asText(""), date(p, "eventAt"), key); }
    private CareData.ReminderCommand reminder(JsonNode p) {
        return new CareData.ReminderCommand(id(p, "plantId"), text(p, "action"), text(p, "title"), date(p, "dueAt"),
                p.path("repeatDays").isNull() || !p.has("repeatDays") ? null : id(p, "repeatDays"), p.path("repeatMode").asText("calendar"));
    }
    private LocalDateTime date(JsonNode p, String field) {
        try { return LocalDateTime.ofInstant(Instant.parse(text(p, field)), ZoneOffset.UTC); }
        catch (DateTimeException ex) { throw bad("Укажите дату ISO с часовым поясом UTC"); }
    }
    private String fingerprint(Object object) { return identity.key(json.valueToTree(object).toString()); }
    private int id(JsonNode p, String key) {
        if (!p.path(key).canConvertToInt() || !p.path(key).isIntegralNumber() || p.get(key).intValue() < 1) throw bad("Неверный идентификатор или число: " + key);
        return p.get(key).intValue();
    }
    private String text(JsonNode p, String key) { if (!p.path(key).isTextual() || p.get(key).asText().isBlank()) throw bad("Не заполнено поле " + key); return p.get(key).asText(); }
    private DomainException bad(String message) { return new DomainException("unprocessable", message); }
    private Map<String, Object> integer() { return Map.of("type", "integer", "minimum", 1); }
    private Map<String, Object> tool(String name, String description, Map<String, Object> properties, List<String> required) {
        return Map.of("type", "function", "name", name, "description", description, "parameters",
                Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
    }
}
