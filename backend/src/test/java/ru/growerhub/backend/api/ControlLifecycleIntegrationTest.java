package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.ControlData;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.automation.engine.AutomationWorker;
import ru.growerhub.backend.automation.engine.ControlWorker;
import ru.growerhub.backend.automation.engine.RuleWorker;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.engine.DemoWorker;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@SpringBootTest(properties = {
        "MQTT_HOST=", "demo.enabled=true", "demo.history-days=1", "demo.history-step-minutes=360",
        "assistant.hmac-secret=control-schema-test-secret-at-least-32-characters",
        "demo.max-guest-spaces=100", "demo.max-active-spaces=100", "demo.max-creates-per-hour=100",
        "spring.datasource.url=jdbc:h2:mem:control_lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ControlLifecycleIntegrationTest extends IntegrationTestBase {
    private static final Instant START = Instant.parse("2026-10-22T09:00:00Z");
    @Autowired DemoFacade demo;
    @Autowired AutomationFacade automation;
    @Autowired ZigbeeFacade zigbee;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ru.growerhub.backend.advisor.engine.AssistantTools assistantTools;
    @Autowired TestClock clock;
    @MockBean MqttPublisher mqtt;
    @MockBean RuleWorker ruleWorker;
    @MockBean ControlWorker controlWorker;
    @MockBean DemoWorker demoWorker;
    @MockBean AutomationWorker automationWorker;
    AuthenticatedUser owner;
    Greenhouse zone;
    ResourceBinding source;
    ResourceBinding target;

    static class TestClock extends Clock {
        Instant value = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfig {
        @Bean @Primary TestClock controlLifecycleClock() { return new TestClock(); }
    }

    @BeforeEach void prepareDemo() {
        clock.value = START;
        jdbc.update("MERGE INTO demo_capacity (id) KEY(id) VALUES (1)");
        var space = demo.createGuest("ru", "Europe/Moscow", "rule-priority-" + UUID.randomUUID());
        owner = new AuthenticatedUser(space.dataUserId(), "demo");
        var farm = automation.getFarmsOverview(owner).farms().getFirst();
        zone = farm.greenhouses().getFirst();
        source = zone.slots().stream().filter(s -> "LIGHT_SWITCH".equals(s.role())).findFirst().orElseThrow();
        target = zone.slots().stream().filter(s -> "EXHAUST_SWITCH".equals(s.role())).findFirst().orElseThrow();
        automation.setUserScenariosEnabled(owner, new SetScenariosEnabledRequest(false));
        setFact(source, "ON");
        setFact(target, "OFF");
        clearInvocations(mqtt);
    }


    @Test void legacySetDoesNotBecomeAnEndlessHoldAfterItsAcknowledgement() {
        var rule = automation.saveRule(null, new Save(null, true, new Definition(2, "Разовая команда", "BOX", zone.id(), interval(), null,
                List.of(json.valueToTree(Map.of("type", "set", "resourceId", target.id(), "value", "ON"))), 60)), owner);
        startRules(); driveControls(); clock.value = START.plusSeconds(5); startRules(); startRules();
        assertThat(automation.ruleHistory(rule.id(), owner).getFirst().status()).isEqualTo("completed");
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        clock.value = START.plusSeconds(10);
        setFact(target, "OFF");
        driveControls(); startRules();
        assertThat(current(target)).isEqualTo("OFF");
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        verifyNoInteractions(mqtt);
    }

    @Test void interruptionIsRecordedAsPartialWithoutExtendingTheOriginalDeadline() {
        var rule = automation.saveRule(null, new Save(null, true, hold("Минимальное проветривание", "ON", 180, 50, interval(), null)), owner);
        startRules(); driveControls();
        var original = automation.getControl(target.id(), owner).expiresAt();
        clock.value = START.plusSeconds(10);
        automation.setManualControl(target.id(), new ControlData.Manual(UUID.randomUUID(), "OFF", 30), owner);
        driveControls();
        assertThat(current(target)).isEqualTo("OFF");
        clock.value = START.plusSeconds(41);
        driveControls();
        assertThat(current(target)).isEqualTo("ON");
        assertThat(automation.getControl(target.id(), owner).expiresAt()).isEqualTo(original);
        clock.value = START.plusSeconds(181);
        driveControls(); startRules();
        var run = automation.ruleHistory(rule.id(), owner).getFirst();
        assertThat(run.status()).isEqualTo("partial");
        assertThat(run.result().toString()).contains("\"interrupted\":true", "\"completed\":false");
        assertThat(current(target)).isEqualTo("ON");
        verifyNoInteractions(mqtt);
    }

    @Test void disabledGreenhouseBetweenClaimAndDispatchPreventsPublishing() {
        automation.saveRule(null, new Save(null, true, hold("Не включать выключенную теплицу", "ON", 60, 50, interval(), null)), owner);
        startRules();
        var step = automation.claimControlCommand(owner.id());
        assertThat(step).isNotNull();
        automation.updateGreenhouse(owner, zone.id(), new SaveGreenhouseRequest(null, null, false));
        assertThatThrownBy(() -> automation.executeControlCommand(step)).isInstanceOf(DomainException.class);
        automation.finishControlCommand(step, false);
        driveControls();
        assertThat(current(target)).isEqualTo("OFF");
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        verifyNoInteractions(mqtt);
    }

    @Test void conditionChangedAfterClaimIsRecheckedBeforePublishing() {
        automation.saveRule(null, new Save(null, true, hold("Только при свете", "ON", 60, 50, repeat("immediate", "finish"), condition(60))), owner);
        startRules();
        var step = automation.claimControlCommand(owner.id());
        assertThat(step).isNotNull();
        clock.value = START.plusSeconds(1);
        setFact(source, "OFF");
        assertThatThrownBy(() -> automation.executeControlCommand(step)).isInstanceOf(DomainException.class);
        automation.finishControlCommand(step, false);
        driveControls();
        assertThat(current(target)).isEqualTo("OFF");
        verifyNoInteractions(mqtt);
    }

    @Test void unrelatedPowerTelemetryDoesNotKeepLightConditionFresh() {
        automation.saveRule(null, new Save(null, true, hold("Проветривание", "ON", 180, 50, repeat("immediate", "finish"), condition(30))), owner);
        startRules(); driveControls();
        var coordinator = zigbee.getSimulationCoordinator(owner.id());
        String name = zigbee.getDevicesForUser(owner).stream().filter(d -> d.device().ieeeAddress().equals(source.zigbeeIeeeAddress()))
                .findFirst().orElseThrow().device().friendlyName();
        clock.value = START.plusSeconds(31);
        zigbee.recordSimulatedSnapshot(coordinator.id(), ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType.DEVICE_STATE,
                name, Map.of("power", 10), java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
        var observed = zigbee.controlObservation(owner, coordinator.publicId(), source.zigbeeIeeeAddress(), source.commandProperty());
        assertThat(observed.value()).isEqualTo("ON");
        assertThat(observed.observedAt()).isEqualTo(java.time.LocalDateTime.ofInstant(START, java.time.ZoneOffset.UTC));
        startRules(); driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        verifyNoInteractions(mqtt);
    }

    @Test void hysteresisBaselineKeepsItsOwnStateDuringLongOverride() {
        var configs = automation.getFarmsOverview(owner).farms().getFirst().greenhouses().stream()
                .filter(g -> g.id().equals(zone.id())).findFirst().orElseThrow().scenarios();
        automation.replaceGreenhouseScenarios(owner, zone.id(), new SaveScenariosRequest(configs.stream()
                .map(c -> new ScenarioConfigRequest(c.scenarioType(), "BOX_CLIMATE".equals(c.scenarioType()), c.config())).toList()));
        var temperature = zone.slots().stream().filter(r -> "AIR_TEMPERATURE_SENSOR".equals(r.role())).findFirst().orElseThrow();
        var environmentId = jdbc.queryForObject("select d.id from demo_devices d join sensors s on s.device_id=d.native_device_id where s.id=?",
                UUID.class, temperature.nativeSensorId());
        demo.environment(owner, new ru.growerhub.backend.demo.contract.DemoData.Environment(environmentId, 26d, null, null));
        automation.evaluateDemoOwner(owner.id()); driveControls();
        assertThat(automation.getControl(target.id(), owner).desiredValue()).isEqualTo("OFF");
        automation.saveRule(null, new Save(null, true, hold("Длинное проветривание", "ON", 180, 50, interval(), null)), owner);
        startRules(); driveControls();
        for (int seconds : List.of(30, 60, 90, 120, 150)) {
            clock.value = START.plusSeconds(seconds);
            demo.environment(owner, new ru.growerhub.backend.demo.contract.DemoData.Environment(environmentId, 27.5d, null, null));
            automation.evaluateDemoOwner(owner.id()); driveControls();
        }
        clock.value = START.plusSeconds(181);
        automation.evaluateDemoOwner(owner.id()); driveControls();
        assertThat(automation.getControl(target.id(), owner).sourceType()).isEqualTo("scenario");
        assertThat(automation.getControl(target.id(), owner).desiredValue()).isEqualTo("OFF");
        assertThat(current(target)).isEqualTo("OFF");
        verifyNoInteractions(mqtt);
    }

    @Test void builtInHistoryRecordsActualAcknowledgementOnceAndDoesNotCallPendingAnAction() {
        var configs = automation.getFarmsOverview(owner).farms().getFirst().greenhouses().stream()
                .filter(g -> g.id().equals(zone.id())).findFirst().orElseThrow().scenarios();
        automation.replaceGreenhouseScenarios(owner, zone.id(), new SaveScenariosRequest(configs.stream()
                .map(c -> new ScenarioConfigRequest(c.scenarioType(), "BOX_CLIMATE".equals(c.scenarioType()), c.config())).toList()));
        var temperature = zone.slots().stream().filter(r -> "AIR_TEMPERATURE_SENSOR".equals(r.role())).findFirst().orElseThrow();
        var environmentId = jdbc.queryForObject("select d.id from demo_devices d join sensors s on s.device_id=d.native_device_id where s.id=?",
                UUID.class, temperature.nativeSensorId());
        clock.value = START.plusSeconds(1);
        demo.environment(owner, new ru.growerhub.backend.demo.contract.DemoData.Environment(environmentId, 34d, null, null));
        var count = jdbc.queryForObject("select count(*) from automation_action_log where resource_binding_id=?", Integer.class, target.id());
        var previousActions = jdbc.queryForList("select last_action_at from automation_scenario_states where scope_type='BOX' and scope_id=? and scenario_type='BOX_CLIMATE'",
                java.time.LocalDateTime.class, zone.id());
        var originalLastAction = previousActions.isEmpty() ? null : previousActions.getFirst();
        automation.evaluateDemoOwner(owner.id());
        var queued = new java.util.ArrayList<ControlData.Step>();
        for (int n = 0; n < 8; n++) {
            var step = automation.claimControlCommand(owner.id());
            if (step == null) break;
            queued.add(step);
        }
        var command = queued.stream().filter(step -> target.zigbeeIeeeAddress().equals(step.ieeeAddress())
                && target.commandProperty().equals(step.property())).findFirst().orElse(null);
        assertThat(command).as("pending command for %s", automation.getControl(target.id(), owner)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from automation_action_log where resource_binding_id=?", Integer.class, target.id())).isEqualTo(count);
        assertThat(jdbc.queryForObject("select last_action_at from automation_scenario_states where scope_type='BOX' and scope_id=? and scenario_type='BOX_CLIMATE'",
                java.time.LocalDateTime.class, zone.id())).isEqualTo(originalLastAction);
        clock.value = START.plusSeconds(2);
        automation.executeControlCommand(command); automation.finishControlCommand(command, true);
        assertThat(jdbc.queryForObject("select count(*) from automation_action_log where resource_binding_id=?", Integer.class, target.id())).isEqualTo(count);
        automation.reconcileControls(owner.id());
        assertThat(jdbc.queryForObject("select count(*) from automation_action_log where resource_binding_id=? and result='confirmed'", Integer.class, target.id()))
                .as("confirmed history for %s; channel=%s", command, automation.getControl(target.id(), owner)).isEqualTo(1);
        for (var other : queued) if (!other.commandId().equals(command.commandId())) {
            automation.executeControlCommand(other); automation.finishControlCommand(other, true);
        }
        var acknowledgedAt = java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (int n = 0; n < 3; n++) {
            clock.value = START.plusSeconds(2 + n);
            automation.evaluateDemoOwner(owner.id()); driveControls();
        }
        assertThat(jdbc.queryForObject("select count(*) from automation_action_log where resource_binding_id=?", Integer.class, target.id())).isEqualTo(count + 1);
        assertThat(jdbc.queryForObject("select last_action_at from automation_scenario_states where scope_type='BOX' and scope_id=? and scenario_type='BOX_CLIMATE'",
                java.time.LocalDateTime.class, zone.id())).isEqualTo(acknowledgedAt);
        verifyNoInteractions(mqtt);
    }

    @Test void advertisedRepeatHoldShapeAndItsExamplePrepareADisabledRuleWithoutCommands() throws Exception {
        var schema = json.valueToTree(automation.ruleSchema());
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) schema.path("examples").path("repeatHoldWhileLightOn").path("payload").deepCopy();
        var definition = (com.fasterxml.jackson.databind.node.ObjectNode) payload.path("definition");
        definition.put("scopeId", zone.id());
        ((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("condition")).put("resourceId", source.id());
        ((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("actions").get(0)).put("resourceId", target.id());
        var triggerPrototype = (com.fasterxml.jackson.databind.node.ObjectNode) java.util.stream.StreamSupport.stream(schema.path("triggers").spliterator(), false)
                .filter(node -> "repeat_while".equals(node.path("type").asText())).findFirst().orElseThrow().deepCopy();
        var actionPrototype = (com.fasterxml.jackson.databind.node.ObjectNode) java.util.stream.StreamSupport.stream(schema.path("actions").spliterator(), false)
                .filter(node -> "hold".equals(node.path("type").asText())).findFirst().orElseThrow().deepCopy();
        definition.path("trigger").properties().forEach(field -> triggerPrototype.set(field.getKey(), field.getValue()));
        definition.path("actions").get(0).properties().forEach(field -> actionPrototype.set(field.getKey(), field.getValue()));
        var prototypeDefinition = definition.deepCopy();
        prototypeDefinition.set("trigger", triggerPrototype);
        ((com.fasterxml.jackson.databind.node.ArrayNode) prototypeDefinition.path("actions")).set(0, actionPrototype);
        var checked = automation.previewRule(null, json.treeToValue(prototypeDefinition, Definition.class), owner);
        assertThat(checked.resources()).containsExactlyInAnyOrder(source.id(), target.id());
        var prepared = assistantTools.prepare("rule", payload, owner);
        assertThat(prepared.payload().path("enabled").asBoolean()).isFalse();
        assertThat(prepared.payload().path("timing").isObject()).isTrue();
        assertThat(prepared.payload().path("coordination").isEmpty()).isFalse();
        assertThat(automation.listRules(owner)).isEmpty();
        assertThat(automation.listControls(owner)).allSatisfy(status -> assertThat(status.demands()).isEmpty());
        verifyNoInteractions(mqtt);
    }

    private Definition hold(String name, String value, int seconds, int priority, JsonNode trigger, JsonNode condition) {
        return new Definition(3, name, "BOX", zone.id(), trigger, condition,
                List.of(json.valueToTree(Map.of("type", "hold", "resourceId", target.id(), "value", value,
                        "durationSeconds", seconds, "priority", priority))), 60);
    }

    private JsonNode interval() {
        return json.valueToTree(Map.of("type", "interval", "seconds", 3600, "anchor", START.toString()));
    }

    private JsonNode repeat(String first, String loss) {
        return json.valueToTree(Map.of("type", "repeat_while", "seconds", 60, "first", first, "onConditionLoss", loss));
    }

    private JsonNode condition(int maxAge) {
        return json.valueToTree(Map.of("type", "sensor", "resourceId", source.id(), "op", "eq", "value", "ON", "maxAgeSeconds", maxAge));
    }

    private void startRules() {
        automation.evaluateRules(owner.id());
        for (int i = 0; i < 8; i++) {
            var step = automation.claimRuleStep(owner.id());
            if (step == null) break;
            automation.finishRuleStep(step, automation.executeRuleStep(step), true);
        }
    }

    private void driveControls() {
        for (int pass = 0; pass < 3; pass++) {
            automation.reconcileControls(owner.id());
            for (int i = 0; i < 8; i++) {
                var step = automation.claimControlCommand(owner.id());
                if (step == null) break;
                automation.executeControlCommand(step);
                automation.finishControlCommand(step, true);
            }
        }
    }

    private void setFact(ResourceBinding binding, String value) {
        zigbee.setDeviceProperty(owner, binding.zigbeeCoordinatorId(), binding.zigbeeIeeeAddress(), binding.commandProperty(), value);
    }

    private Object current(ResourceBinding binding) {
        return automation.getFarmsOverview(owner).farms().getFirst().greenhouses().stream()
                .flatMap(g -> g.slots().stream()).filter(s -> s.id().equals(binding.id())).findFirst().orElseThrow().currentValue();
    }
}
