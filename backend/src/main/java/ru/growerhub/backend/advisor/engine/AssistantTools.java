package ru.growerhub.backend.advisor.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ResourceLoader;
import ru.growerhub.backend.advisor.jpa.AssistantPhotoRepository;
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
    private final AssistantRequests requests;
    private final JsonNode interfaceGuide;
    private final AssistantPhotoRepository photos;

    public AssistantTools(AutomationFacade automation, PlantFacade plants, JournalFacade journal, UserFacade users,
            AssistantIdentity identity, AssistantSettings settings, ObjectMapper json, Clock clock, AssistantRequests requests, ResourceLoader resources, AssistantPhotoRepository photos) {
        this.automation = automation; this.plants = plants; this.journal = journal; this.users = users;
        this.identity = identity; this.settings = settings; this.json = json; this.clock = clock; this.requests = requests;
        this.photos = photos;
        try (var input = resources.getResource(settings.interfaceGuide()).getInputStream()) { this.interfaceGuide = json.readTree(input); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Не удалось загрузить справку интерфейса", ex); }
    }
    public record Prepared(String title, JsonNode payload, String fingerprint) {}

    public boolean adminRead(AuthenticatedUser user) {
        if (user == null || user.isDemo() || !user.id().equals(settings.ownerUserId())) return false;
        var account = users.getAuthUser(user.id());
        return account != null && account.active() && "admin".equals(account.role());
    }

    public List<Map<String, Object>> catalog(AuthenticatedUser user) {
        var result = new ArrayList<>(List.of(
            tool("gh_farm_report", "Своя ферма: устройства, показания, сценарии, растения. zoneName — одна теплица. Новое правило: gh_rule_details zoneName уже даёт отчёт и схему.", Map.of("zoneName", Map.of("type", "string")), List.of()),
            tool("gh_app_help", "Навигация: section — раздел, ruleId — ссылка на своё правило. Без section — список разделов. План и конфликты — gh_rule_details query.", Map.of("section", Map.of("type", "string", "enum", interfaceGuide.path("sections").properties().stream().map(Map.Entry::getKey).toList()), "ruleId", Map.of("type", "string")), List.of()),
            tool("gh_plant_details", "Карточка, размещение, первая страница журнала и дела своего растения. ID растения возьми из gh_farm_report. Следующие страницы или поиск записей — gh_journal.", Map.of("plantId", integer()), List.of("plantId")),
            tool("gh_journal", "Свой журнал с фото. Фильтры: plantId, query, action, from/until (ISO UTC), page с нуля. entryId — одна своя запись для исправления. Автоматический полив только читается.",
                Map.of("plantId", integer(), "entryId", integer(), "query", Map.of("type", "string"), "action", Map.of("type", "string"), "from", Map.of("type", "string"), "until", Map.of("type", "string"), "page", Map.of("type", "integer", "minimum", 0)), List.of()),
            tool("gh_resource_history", "История и энергопотребление своей розетки. hours — число часов", Map.of("resourceId", integer(), "hours", integer()), List.of("resourceId", "hours")),
            tool("gh_sensor_history", "История своего датчика за hours часов (до 2160), resourceId из gh_farm_report. Сохранённые точки: учитывай пропуски, свежесть, калибровку почвы. Для сравнения вызови по каждому датчику.", Map.of("resourceId", integer(), "hours", integer()), List.of("resourceId", "hours")),
            tool("gh_feature_requests", "Мои отправленные запросы на доработки и решения команды. page — страница с нуля.", Map.of("page", Map.of("type", "integer", "minimum", 0)), List.of()),
            tool("gh_rule_details", "query=название или ruleId: своё правило, план, даты, конфликты. Новое правило: zoneName даёт отчёт, схему и пример. {} — список и схема. scopeType=ROOM|BOX и scopeId — готовые сценарии.", Map.of("zoneName", Map.of("type", "string"), "query", Map.of("type", "string"), "ruleId", Map.of("type", "string"), "scopeType", Map.of("type", "string"), "scopeId", integer()), List.of()),
            tool("gh_validate_rule", "Проверка definition без карточки. Перед gh_prepare_change не нужен: он сам проверяет. payload — definition из gh_rule_details.", Map.of("payload", Map.of("type", "object")), List.of("payload")),
            tool("gh_prepare_change", "Подготовить предложение, НЕ применяя его. kind=rule сам проверяет; отдельный gh_validate_rule не нужен. kind=plant: {operation:create|update,plantId?:number,fields:{name?,plantType?,strain?,growthStage?,description?,locationLabel?,avatarKey?:tomato|cucumber|strawberry|rosemary|basil|lettuce|pepper|petunia|pelargonium|monstera,avatarStage?:sprout|young|mature|peak,plantedAt?:ISO UTC,dateUnknown?:boolean},zoneId?:number|null}; zoneId — ID теплицы из gh_farm_report, null убирает размещение, отсутствие сохраняет прежнее. Для create обязательно fields.name, неизвестную дату не выдумывай. Картинка и avatarStage не меняют growthStage и автоматику; названия стадий есть в gh_plant_details.avatarOptions. kind=care: {plantId,entryId?:number,action,text,eventAt?:ISO UTC,photoIds?:UUID[]}; entryId исправляет ручную запись, иначе создаёт новую. Фото бери только из списка вложений этого разговора. action=watering|fertilizing|repotting|pruning|treatment|inspection|photo|note. reminder: {plantId,action,title,dueAt,repeatDays,repeatMode}; scenario: {scopeType:ROOM|BOX,scopeId,scenarioType,enabled,config}; rule: {ruleId?:UUID,revision?:number,enabled:boolean,definition:object из gh_rule_details}; device: {type:set|water,resourceId,value?:string,ttlSeconds?:integer (обязательно для set, явный срок ручного управления),durationSeconds?:integer (для water)}; feature_request: {title до 120 символов,description до 2000,benefit до 1000}. Человек подтверждает кнопкой. Чужие данные и владельца указывать нельзя.",
                Map.of("kind", Map.of("type", "string", "enum", List.of("plant", "care", "reminder", "scenario", "rule", "device", "feature_request")), "payload", Map.of("type", "object")), List.of("kind", "payload"))));
        if (adminRead(user)) {
            result.add(tool("gh_admin_users", "Только для владельца-администратора: поиск аккаунтов по имени, email или ID. Без секретов. offset — смещение, до 20 записей.", Map.of("query", Map.of("type", "string", "maxLength", 100), "offset", Map.of("type", "integer", "minimum", 0)), List.of()));
            result.add(tool("gh_admin_user_report", "Только чтение данных выбранного пользователя: профиль, ферма, устройства, растения, сценарии. Не даёт права менять чужие данные.", Map.of("userId", integer()), List.of("userId")));
            result.add(tool("gh_admin_system", "Сводка аккаунтов GrowerHub. Только чтение, без shell, секретов и произвольных запросов к БД.", Map.of(), List.of()));
        }
        return List.copyOf(result);
    }

    public JsonNode read(String name, JsonNode input, AuthenticatedUser user) {
        return switch (name) {
            case "gh_admin_users" -> {
                requireAdminRead(user);
                String query = input.path("query").asText("").strip().toLowerCase(Locale.ROOT);
                if (query.length() > 100 || input.path("offset").asInt(0) < 0) throw bad("Сократите поисковый запрос");
                var found = users.listUsers().stream().filter(u -> (u.id() + " " + u.username() + " " + u.email()).toLowerCase(Locale.ROOT).contains(query)).toList();
                yield json.valueToTree(Map.of("total", found.size(), "users", found.stream().skip(input.path("offset").asInt(0)).limit(20).toList()));
            }
            case "gh_admin_user_report" -> {
                requireAdminRead(user);
                var account = users.getUser(id(input, "userId"));
                if (account == null) throw bad("Пользователь не найден");
                var target = new AuthenticatedUser(account.id(), "user");
                yield json.valueToTree(Map.of("profile", account, "farm", report(target), "rules", automation.listRules(target)));
            }
            case "gh_admin_system" -> {
                requireAdminRead(user);
                var accounts = users.listUsers();
                yield json.valueToTree(Map.of("at", clock.instant(), "accounts", accounts.size(),
                        "activeAccounts", accounts.stream().filter(u -> u.active()).count(),
                        "completedSetup", accounts.stream().filter(u -> u.onboardingCompletedAt() != null).count(),
                        "startedDiary", accounts.stream().filter(u -> u.careStartedAt() != null).count(),
                        "publicAssistantConfigured", settings.publicEnabled(), "monthlyApiLimitUsd", settings.budget().monthMicros() / 1000000d));
            }
            case "gh_farm_report" -> report(user, optionalText(input, "zoneName"));
            case "gh_app_help" -> interfaceHelp(input, user);
            case "gh_feature_requests" -> json.valueToTree(requests.list(user, false, input.path("page").asInt(0)));
            case "gh_journal" -> {
                if (input.hasNonNull("entryId")) yield json.valueToTree(journal.getCareEntry(id(input, "entryId"), user));
                int page = input.has("page") ? nonNegative(input, "page") : 0;
                yield json.valueToTree(Map.of("now", clock.instant(), "page", journal.searchCare(user, input.hasNonNull("plantId") ? id(input, "plantId") : null,
                        optionalText(input, "query"), optionalText(input, "action"), input.hasNonNull("from") ? date(input, "from") : null,
                        input.hasNonNull("until") ? date(input, "until") : null, page)));
            }
            case "gh_sensor_history" -> sensorHistory(input, user);
            case "gh_plant_details" -> {
                int id = id(input, "plantId"); var plant = plants.requireOwnedPlantInfo(id, user);
                var data = new LinkedHashMap<String, Object>(); data.put("plant", plant); data.put("zone", automation.getPlantZone(user, id)); data.put("avatarOptions", plants.avatarOptions());
                data.put("now", clock.instant()); data.put("url", settings.siteUrl() + "/app/plants/" + id + "/journal/");
                data.put("care", journal.searchCare(user, id, null, null, null, null, 0));
                data.put("reminders", journal.careReminders(user).stream().filter(r -> r.plantId().equals(id)).toList()); yield json.valueToTree(data);
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
                if (input.hasNonNull("ruleId")) {
                    var rule = automation.getRule(uuid(input, "ruleId"), user);
                    var result = rulePreview(rule, user);
                    result.set("history", json.valueToTree(automation.ruleHistory(rule.id(), user)));
                    yield result;
                }
                var rules = automation.listRules(user);
                if (input.hasNonNull("query")) {
                    String query = text(input, "query").strip().toLowerCase(Locale.ROOT);
                    var exact = rules.stream().filter(rule -> rule.definition().name().toLowerCase(Locale.ROOT).equals(query)).toList();
                    var matched = exact.isEmpty() ? rules.stream().filter(rule -> rule.definition().name().toLowerCase(Locale.ROOT).contains(query)).toList() : exact;
                    if (matched.size() == 1) yield rulePreview(matched.getFirst(), user);
                    yield json.valueToTree(Map.of("rules", ruleSummaries(matched, user), "needsClarification", matched.size() > 1));
                }
                var result = json.createObjectNode();
                result.set("rules", json.valueToTree(ruleSummaries(rules, user)));
                result.set("schema", json.valueToTree(automation.ruleSchema()));
                if (input.hasNonNull("zoneName")) result.set("farm", report(user, text(input, "zoneName")));
                yield result;
            }
            case "gh_validate_rule" -> json.valueToTree(automation.previewRule(null, ruleDefinition(input.get("payload")), user));
            default -> throw bad("Такого инструмента у помощника нет");
        };
    }

    private com.fasterxml.jackson.databind.node.ObjectNode rulePreview(RuleData.Rule rule, AuthenticatedUser user) {
        return json.valueToTree(Map.of("rule", rule, "preview", automation.previewRule(rule.id(), rule.definition(), user), "now", clock.instant()));
    }

    private List<Map<String, Object>> ruleSummaries(List<RuleData.Rule> rules, AuthenticatedUser user) {
        var names = new HashMap<String, String>();
        for (var farm : automation.getFarmsOverview(user).farms()) {
            names.put("ROOM:" + farm.id(), farm.name());
            farm.greenhouses().forEach(zone -> names.put("BOX:" + zone.id(), zone.name()));
        }
        return rules.stream().map(rule -> {
            var definition = rule.definition();
            return Map.<String, Object>of("id", rule.id(), "revision", rule.revision(), "name", definition.name(), "enabled", rule.enabled(),
                    "scopeType", definition.scopeType(), "scopeId", definition.scopeId(),
                    "scopeName", names.getOrDefault(definition.scopeType() + ":" + definition.scopeId(), ""), "description", rule.description());
        }).toList();
    }

    private void requireAdminRead(AuthenticatedUser user) {
        if (!adminRead(user)) throw new DomainException("forbidden", "Административное чтение недоступно");
    }

    private JsonNode sensorHistory(JsonNode input, AuthenticatedUser user) {
        int hours = id(input, "hours");
        if (hours > settings.retentionDays() * 24) throw bad("Слишком длинный период истории");
        var history = automation.getResourceSensorHistory(user, id(input, "resourceId"), hours);
        var points = history.points().stream().filter(p -> p.value() != null && Double.isFinite(p.value()))
                .sorted(Comparator.comparing(ResourceStatisticsPoint::ts)).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("resourceId", history.resourceId()); result.put("name", history.name()); result.put("role", history.role()); result.put("unit", history.unit());
        result.put("fromUtc", history.from()); result.put("untilUtc", history.until()); result.put("savedPoints", points.size());
        result.put("basis", "Сохранённые точки; источник может усреднять историю. Минимум и максимум не гарантируют учёт кратких пиков. Пропуски не заполнены.");
        if ("SOIL_MOISTURE_SENSOR".equals(history.role())) result.put("interpretation", "Проценты зависят от датчика и грунта. Без сухого/влажного ориентира нельзя объявлять перелив или нехватку воды только по абсолютному значению.");
        if (!points.isEmpty()) {
            var first = points.get(0); var last = points.get(points.size() - 1);
            result.put("first", first); result.put("last", last);
            result.put("minimum", points.stream().min(Comparator.comparing(ResourceStatisticsPoint::value)).orElseThrow());
            result.put("maximum", points.stream().max(Comparator.comparing(ResourceStatisticsPoint::value)).orElseThrow());
            result.put("sampleMean", points.stream().mapToDouble(ResourceStatisticsPoint::value).average().orElseThrow());
            result.put("change", last.value() - first.value());
            result.put("lastAgeSeconds", Duration.between(last.ts(), history.until()).getSeconds());
            long gap = 0;
            for (int i = 1; i < points.size(); i++) gap = Math.max(gap, Duration.between(points.get(i - 1).ts(), points.get(i).ts()).getSeconds());
            result.put("largestGapSeconds", gap);
        }
        int limit = Math.max(2, settings.historyPoints());
        var shown = new ArrayList<ResourceStatisticsPoint>();
        for (int i = 0, size = Math.min(points.size(), limit); i < size; i++) shown.add(points.get(size == 1 ? 0 : (int) ((long) i * (points.size() - 1) / (size - 1))));
        result.put("sampled", shown.size() < points.size()); result.put("points", shown);
        return json.valueToTree(result);
    }

    private JsonNode interfaceHelp(JsonNode input, AuthenticatedUser user) {
        String section = input.hasNonNull("ruleId") ? "scenarios" : input.path("section").asText("");
        if (!section.isEmpty() && !interfaceGuide.path("sections").has(section)) throw bad("Такого раздела в справке нет");
        var result = json.createObjectNode(); result.put("version", interfaceGuide.path("version").asInt());
        result.put("demo", user.isDemo()); result.set("instructions", interfaceGuide.get("instructions"));
        var pages = result.putArray("sections");
        interfaceGuide.path("sections").properties().forEach(entry -> {
            if (section.isEmpty() || section.equals(entry.getKey())) {
                var page = (com.fasterxml.jackson.databind.node.ObjectNode) entry.getValue().deepCopy();
                page.put("section", entry.getKey()); page.put("url", settings.siteUrl() + page.path("path").asText()); pages.add(page);
            }
        });
        if (section.equals("scenarios")) {
            var ownRules = input.hasNonNull("ruleId") ? List.of(automation.getRule(uuid(input, "ruleId"), user)) : automation.listRules(user);
            var names = new HashMap<String, String>();
            for (var farm : automation.getFarmsOverview(user).farms()) {
                names.put("ROOM:" + farm.id(), farm.name());
                farm.greenhouses().forEach(g -> names.put("BOX:" + g.id(), g.name()));
            }
            result.set("rules", json.valueToTree(ownRules.stream().map(rule -> Map.of("id", rule.id(), "name", rule.definition().name(),
                    "scopeType", rule.definition().scopeType(), "scopeName", names.getOrDefault(rule.definition().scopeType() + ":" + rule.definition().scopeId(), ""),
                    "enabled", rule.enabled(), "url", settings.siteUrl() + interfaceGuide.path("sections").path("scenarios").path("path").asText() + "?rule=" + rule.id())).toList()));
        }
        return result;
    }

    public JsonNode report(AuthenticatedUser user) { return report(user, null); }

    private JsonNode report(AuthenticatedUser user, String zoneName) {
        var overview = automation.getFarmsOverview(user);
        String focus = zoneName == null ? null : zoneName.strip().toLowerCase(Locale.ROOT);
        var zones = overview.farms().stream().flatMap(farm -> farm.greenhouses().stream())
                .filter(zone -> focus == null || zone.name().toLowerCase(Locale.ROOT).equals(focus)).toList();
        if (focus != null && zones.size() != 1) throw bad(zones.isEmpty() ? "Теплица с таким названием не найдена" : "Уточните теплицу: найдено несколько одинаковых названий");
        var zoneIds = zones.stream().map(zone -> zone.id()).collect(java.util.stream.Collectors.toSet());
        var scopedFarms = overview.farms().stream().filter(farm -> focus == null || farm.greenhouses().stream().anyMatch(zone -> zoneIds.contains(zone.id()))).toList();
        var controls = automation.listControls(user);
        var controlById = controls.stream().collect(java.util.stream.Collectors.toMap(ru.growerhub.backend.automation.contract.ControlData.Status::resourceId, c -> c));
        List<Map<String, Object>> farms = scopedFarms.stream().map(farm -> {
            var row = new LinkedHashMap<String, Object>(); row.put("id", farm.id()); row.put("name", farm.name());
            row.put("enabled", farm.enabled()); row.put("resources", farm.slots().stream().map(r -> compactResource(r, controlById.get(r.id()))).toList()); row.put("scenarios", farm.scenarios().stream().map(this::compactScenario).toList());
            row.put("greenhouses", farm.greenhouses().stream().filter(zone -> zoneIds.contains(zone.id())).map(zone -> {
                var z = new LinkedHashMap<String, Object>(); z.put("id", zone.id()); z.put("name", zone.name()); z.put("enabled", zone.enabled());
                z.put("resources", zone.slots().stream().map(r -> compactResource(r, controlById.get(r.id()))).toList()); z.put("scenarios", zone.scenarios().stream().map(this::compactScenario).toList());
                z.put("plants", zone.plants().stream().map(p -> Map.of("id", p.id(), "name", p.name())).toList()); return z;
            }).toList()); return (Map<String, Object>) row;
        }).toList();
        var resources = scopedFarms.stream().flatMap(f -> java.util.stream.Stream.concat(f.slots().stream(), f.greenhouses().stream().filter(zone -> zoneIds.contains(zone.id())).flatMap(g -> g.slots().stream()))).toList();
        var resourceIds = resources.stream().map(resource -> resource.id()).collect(java.util.stream.Collectors.toSet());
        var plantIds = zones.stream().flatMap(zone -> zone.plants().stream()).map(plant -> plant.id()).collect(java.util.stream.Collectors.toSet());
        var ownPlants = plants.listPlants(user).stream().filter(plant -> focus == null || plantIds.contains(plant.id())).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("at", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)); result.put("timezone", users.getTimezone(user.id()));
        result.put("demo", user.isDemo()); result.put("farms", farms); result.put("plantCount", ownPlants.size());
        result.put("farmCount", farms.size()); result.put("greenhouseCount", zones.size());
        result.put("resourceCount", resources.size()); result.put("notReadyCount", resources.stream().filter(r -> !r.ready()).count());
        result.put("plants", ownPlants.stream().map(p -> {
            var row = new LinkedHashMap<String, Object>(); row.put("id", p.id()); row.put("name", p.name()); row.put("stage", p.growthStage());
            row.put("type", p.plantType()); row.put("strain", p.strain()); row.put("location", p.locationLabel()); return row;
        }).toList());
        result.put("controls", controls.stream().filter(control -> focus == null || resourceIds.contains(control.resourceId()))
                .filter(control -> control.sourceId() != null || control.error() != null || !control.demands().isEmpty()).toList());
        result.put("reminders", journal.careReminders(user).stream().filter(reminder -> focus == null || plantIds.contains(reminder.plantId())).toList());
        return json.valueToTree(result);
    }
    private Map<String, Object> compactResource(ResourceBinding r, ru.growerhub.backend.automation.contract.ControlData.Status control) {
        var value = new LinkedHashMap<String, Object>(); value.put("id", r.id()); value.put("role", r.role()); value.put("name", r.label());
        value.put("value", control == null ? r.currentValue() : control.actualValue()); value.put("at", control == null ? r.lastSeenAt() : control.actualAt()); value.put("ready", r.ready());
        if (!r.ready()) value.put("reason", r.reason()); if (r.onValue() != null) { value.put("on", r.onValue()); value.put("off", r.offValue()); }
        return value;
    }
    private Map<String, Object> compactScenario(ScenarioConfig s) {
        var value = new LinkedHashMap<String, Object>(); value.put("type", s.scenarioType()); value.put("enabled", s.enabled()); value.put("readiness", s.readiness()); return value;
    }

    public Prepared prepare(String kind, JsonNode payload, AuthenticatedUser user) {
        if (payload == null || !payload.isObject() || payload.toString().length() > settings.maxToolResultChars()) throw bad("Неверное предложение");
        return switch (kind) {
            case "plant" -> preparePlant(payload, user);
            case "feature_request" -> {
                var clean = requests.validate(payload, user);
                yield new Prepared("Отправить команде: " + clean.path("title").asText(), clean, fingerprint(clean));
            }
            case "care" -> {
                int id = id(payload, "plantId"); var plant = plants.requireOwnedPlantInfo(id, user);
                var clean = payload.deepCopy();
                Object previous = "new";
                if (clean.hasNonNull("entryId")) {
                    var entry = journal.getCareEntry(id(clean, "entryId"), user);
                    if (!entry.entry().plantId().equals(id)) throw bad("Запись относится к другому растению");
                    var object = (com.fasterxml.jackson.databind.node.ObjectNode) clean;
                    if (!clean.has("text")) object.put("text", entry.entry().text());
                    if (!clean.has("action") && entry.action() != null) object.put("action", entry.action());
                    if (!clean.hasNonNull("eventAt")) object.put("eventAt", entry.entry().eventAt().toInstant(ZoneOffset.UTC).toString());
                    previous = entry;
                }
                if (!clean.hasNonNull("eventAt")) ((com.fasterxml.jackson.databind.node.ObjectNode) clean).put("eventAt", clock.instant().toString());
                var command = care(clean, "assistant:preview");
                if (clean.hasNonNull("entryId")) journal.validateCareEntryUpdate(id(clean, "entryId"), user, command);
                else journal.validateCareEntry(id, user, command);
                var ids = photoIds(clean);
                if ("photo".equals(command.action()) && ids.isEmpty() && !clean.hasNonNull("entryId")) throw bad("Прикрепите фотографию для записи в журнал");
                var imageHashes = ids.stream().map(image -> List.of(image, imageFingerprint(image, user))).toList();
                ((com.fasterxml.jackson.databind.node.ObjectNode) clean).put("plantName", plant.name());
                yield new Prepared((clean.hasNonNull("entryId") ? "Исправить запись: " : "Записать уход: ") + plant.name(), clean, fingerprint(List.of(plant, previous, imageHashes)));
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
                ((com.fasterxml.jackson.databind.node.ObjectNode) shown).set("timing", json.valueToTree(check.timing()));
                ((com.fasterxml.jackson.databind.node.ObjectNode) shown).set("coordination", json.valueToTree(check.coordination()));
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
            case "device" -> {
                var normalized = payload.deepCopy();
                if ("set".equals(payload.path("type").asText()) && !payload.has("ttlSeconds"))
                    throw bad("Укажите ttlSeconds — на сколько секунд включить ручное управление");
                var preview = automation.previewDeviceCommand(normalized, user);
                if ("set".equals(payload.path("type").asText())) ((com.fasterxml.jackson.databind.node.ObjectNode) normalized).set("value", preview.path("action").path("value"));
                yield new Prepared("Команда устройству: " + automation.previewDeviceCommand(normalized, user).path("device").asText(), normalized,
                        fingerprint(automation.previewDeviceCommand(normalized, user)));
            }
            default -> throw bad("Этот вид изменения недоступен");
        };
    }

    public JsonNode apply(String kind, JsonNode payload, String expected, UUID proposalId, AuthenticatedUser user) {
        if (!"feature_request".equals(kind)) { if (user.isDemo()) users.lockDemoOwner(user.id()); else users.lockAccount(user.id()); }
        var prepared = prepare(kind, payload, user);
        if (!prepared.fingerprint().equals(expected)) throw new DomainException("conflict", "Настройки изменились. Попросите помощника подготовить новое предложение");
        return switch (kind) {
            case "plant" -> {
                var f = payload.path("fields"); Integer zone = payload.hasNonNull("zoneId") ? id(payload, "zoneId") : null;
                yield json.valueToTree("create".equals(text(payload, "operation"))
                        ? automation.createPlantWithPlacement(user, plantCreate(f), zone)
                        : automation.updatePlantWithPlacement(user, id(payload, "plantId"), plantUpdate(f), payload.has("zoneId"), zone));
            }
            case "feature_request" -> json.valueToTree(requests.create(proposalId, payload, user));
            case "care" -> {
                var entry = payload.hasNonNull("entryId") ? journal.updateCareEntry(id(payload, "entryId"), user, care(payload, "assistant:" + proposalId))
                        : journal.createCareEntry(id(payload, "plantId"), user, care(payload, "assistant:" + proposalId));
                for (UUID image : photoIds(payload)) journal.addCarePhoto(entry.entry().id(), user, ownedPhoto(image, user).bytes);
                yield json.valueToTree(journal.getCareEntry(entry.entry().id(), user));
            }
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
            case "device" -> {
                var action = payload.deepCopy();
                if ("set".equals(action.path("type").asText())) ((com.fasterxml.jackson.databind.node.ObjectNode) action).put("requestId", proposalId.toString());
                yield automation.applyDeviceCommand(action, user);
            }
            default -> throw bad("Неподдерживаемое изменение");
        };
    }
    private UUID uuid(JsonNode node, String field) { try { return UUID.fromString(text(node, field)); } catch (IllegalArgumentException ex) { throw bad("Неверный идентификатор правила"); } }
    private Prepared preparePlant(JsonNode payload, AuthenticatedUser user) {
        String operation = text(payload, "operation"); var clean = (com.fasterxml.jackson.databind.node.ObjectNode) payload.deepCopy(); var fields = clean.path("fields");
        if (!fields.isObject() || !Set.of("create", "update").contains(operation)) throw bad("Укажите создание или изменение растения и его поля");
        var allowed = Set.of("name", "plantType", "strain", "growthStage", "description", "locationLabel", "plantedAt", "dateUnknown", "avatarKey", "avatarStage");
        fields.properties().forEach(e -> { if (!allowed.contains(e.getKey()) || e.getValue().isNull()
                || ("dateUnknown".equals(e.getKey()) ? !e.getValue().isBoolean() : !e.getValue().isTextual())) throw bad("Проверьте поле растения: " + e.getKey()); });
        if (fields.path("dateUnknown").asBoolean() && fields.has("plantedAt")) throw bad("Укажите дату посадки или оставьте её неизвестной");
        if (fields.has("avatarKey") && !fields.has("avatarStage")) {
            var avatar = plants.avatarOptions().stream().filter(a -> a.id().equals(fields.path("avatarKey").asText())).findFirst().orElse(null);
            ((com.fasterxml.jackson.databind.node.ObjectNode) fields).put("avatarStage", avatar == null ? "" : avatar.stages().getLast().id());
        }
        if ("create".equals(operation) && !fields.has("plantedAt")) {
            var normalized = (com.fasterxml.jackson.databind.node.ObjectNode) fields;
            if (fields.has("dateUnknown") && !fields.path("dateUnknown").asBoolean()) normalized.put("plantedAt", clock.instant().toString());
            else normalized.put("dateUnknown", true);
        }
        ru.growerhub.backend.plant.contract.PlantInfo previous = null;
        if ("create".equals(operation)) plants.validatePlantCreate(plantCreate(fields), user);
        else {
            int plantId = id(payload, "plantId"); previous = plants.requireOwnedPlantInfo(plantId, user);
            plants.validatePlantUpdate(plantId, plantUpdate(fields), user);
            if (fields.isEmpty() && !payload.has("zoneId")) throw bad("Укажите, что изменить у растения");
        }
        var oldZone = previous == null ? null : automation.getPlantZone(user, previous.id());
        var zone = oldZone;
        if (payload.has("zoneId") || "create".equals(operation)) {
            zone = null;
            if (payload.hasNonNull("zoneId")) {
                int zoneId = id(payload, "zoneId"); var overview = automation.getFarmsOverview(user);
                for (var farm : overview.farms()) for (var box : farm.greenhouses()) if (box.id().equals(zoneId))
                    zone = new ZoneReference(box.id(), box.name(), farm.id(), farm.name());
                if (zone == null) throw bad("Теплица не найдена в вашей ферме");
            }
        }
        clean.put("plantName", fields.has("name") ? fields.path("name").asText() : previous.name());
        clean.put("zoneName", zone == null ? "" : zone.name()); clean.put("previousZoneName", oldZone == null ? "" : oldZone.name());
        clean.put("zoneChanged", payload.has("zoneId") || "create".equals(operation));
        clean.set("before", json.valueToTree(previous));
        if (fields.has("avatarKey") || fields.has("avatarStage")) {
            String key = fields.has("avatarKey") ? fields.path("avatarKey").asText() : previous == null ? null : previous.avatarKey();
            String stage = fields.has("avatarStage") ? fields.path("avatarStage").asText() : previous == null ? null : previous.avatarStage();
            clean.put("pictureName", pictureLabel(key, stage));
            clean.put("previousPictureName", previous == null ? "" : pictureLabel(previous.avatarKey(), previous.avatarStage()));
        }
        return new Prepared(("create".equals(operation) ? "Добавить растение: " : "Изменить растение: ") + clean.path("plantName").asText(), clean,
                fingerprint(Arrays.asList(previous, oldZone, zone)));
    }
    private String pictureLabel(String key, String stage) {
        var avatar = plants.avatarOptions().stream().filter(a -> a.id().equals(key)).findFirst().orElse(null);
        if (avatar == null) return "Автоматически";
        var variant = avatar.stages().stream().filter(s -> s.id().equals(stage)).findFirst().orElse(avatar.stages().getLast());
        return avatar.nameRu() + " · " + variant.nameRu();
    }
    private PlantFacade.PlantCreateCommand plantCreate(JsonNode f) {
        return new PlantFacade.PlantCreateCommand(optionalText(f, "name"), f.has("plantedAt") ? date(f, "plantedAt") : null,
                optionalText(f, "plantType"), optionalText(f, "strain"), optionalText(f, "growthStage"), optionalText(f, "description"), optionalText(f, "locationLabel"),
                f.has("dateUnknown") ? f.path("dateUnknown").asBoolean() : !f.has("plantedAt"), optionalText(f, "avatarKey"), optionalText(f, "avatarStage"));
    }
    private PlantFacade.PlantUpdateCommand plantUpdate(JsonNode f) {
        return new PlantFacade.PlantUpdateCommand(optionalText(f, "name"), f.has("plantedAt") ? date(f, "plantedAt") : null,
                optionalText(f, "plantType"), optionalText(f, "strain"), optionalText(f, "growthStage"), optionalText(f, "description"), optionalText(f, "locationLabel"), f.path("dateUnknown").asBoolean(false), optionalText(f, "avatarKey"), optionalText(f, "avatarStage"));
    }
    public List<UUID> photoIds(JsonNode payload) {
        if (!payload.has("photoIds")) return List.of();
        var values = payload.get("photoIds");
        if (!values.isArray() || values.size() > settings.limits().maxPhotos()) throw bad("Проверьте фотографии записи");
        var ids = new ArrayList<UUID>();
        for (var value : values) try { if (!value.isTextual()) throw new IllegalArgumentException(); ids.add(UUID.fromString(value.asText())); }
        catch (IllegalArgumentException ex) { throw bad("Неверный идентификатор фотографии"); }
        if (ids.stream().distinct().count() != ids.size()) throw bad("Фотография указана несколько раз");
        return List.copyOf(ids);
    }
    private ru.growerhub.backend.advisor.jpa.AssistantPhotoEntity ownedPhoto(UUID id, AuthenticatedUser user) {
        var image = photos.findById(id).orElseThrow(() -> bad("Фотография не найдена"));
        if (!user.id().equals(image.userId) || !"ready".equals(image.status) || image.bytes == null) throw bad("Фотография недоступна");
        return image;
    }
    private String imageFingerprint(UUID id, AuthenticatedUser user) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(ownedPhoto(id, user).bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String optionalText(JsonNode node, String field) {
        if (!node.hasNonNull(field)) return null;
        if (!node.path(field).isTextual()) throw bad("Проверьте текст: " + field);
        return node.path(field).asText();
    }
    private int nonNegative(JsonNode node, String field) {
        if (!node.path(field).isIntegralNumber() || !node.path(field).canConvertToInt() || node.path(field).asInt() < 0) throw bad("Неверная страница");
        return node.path(field).asInt();
    }
    private RuleData.Definition ruleDefinition(JsonNode node) { try { return json.treeToValue(node, RuleData.Definition.class); } catch (Exception ex) { throw bad("Проверьте структуру правила"); } }
    private List<ScenarioConfig> scenarioSource(JsonNode payload, AuthenticatedUser user) {
        var overview = automation.getFarmsOverview(user); int id = id(payload, "scopeId");
        return switch (text(payload, "scopeType")) {
            case "ROOM" -> overview.farms().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow(() -> bad("Ферма не найдена")).scenarios();
            case "BOX" -> overview.farms().stream().flatMap(f -> f.greenhouses().stream()).filter(g -> g.id().equals(id)).findFirst().orElseThrow(() -> bad("Теплица не найдена")).scenarios();
            default -> throw bad("Выберите ферму или теплицу");
        };
    }
    private CareData.EntryCommand care(JsonNode p, String key) { return new CareData.EntryCommand(p.hasNonNull("entryId") ? optionalText(p, "action") : text(p, "action"), Objects.toString(optionalText(p, "text"), ""), date(p, "eventAt"), key); }
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
