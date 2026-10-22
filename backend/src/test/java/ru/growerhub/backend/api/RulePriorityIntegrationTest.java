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
        "demo.max-guest-spaces=100", "demo.max-active-spaces=100", "demo.max-creates-per-hour=100",
        "spring.datasource.url=jdbc:h2:mem:rule_priority;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RulePriorityIntegrationTest extends IntegrationTestBase {
    private static final Instant START = Instant.parse("2026-10-22T09:00:00Z");
    @Autowired DemoFacade demo;
    @Autowired AutomationFacade automation;
    @Autowired ZigbeeFacade zigbee;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
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
        @Bean @Primary TestClock rulePriorityClock() { return new TestClock(); }
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

    @Test void prioritiesRestorePreviousDemandManualWinsAndHoldEndDoesNotForceOff() {
        setFact(target, "ON");
        var low = automation.saveRule(null, new Save(null, true, hold("Обычный обдув", "ON", 180, 20, interval(), null)), owner);
        var high = automation.saveRule(null, new Save(null, true, hold("Тихий час", "OFF", 60, 80, interval(), null)), owner);
        startRules();
        driveControls();
        var highState = automation.getControl(target.id(), owner);
        assertThat(highState.sourceName()).contains("Тихий час");
        assertThat(highState.desiredValue()).isEqualTo("OFF");
        assertThat(highState.actualConfirmed()).isTrue();
        assertThat(highState.demands()).hasSize(2);
        var highEnd = highState.expiresAt();

        clock.value = START.plusSeconds(2);
        automation.setManualControl(target.id(), new ControlData.Manual(UUID.randomUUID(), "ON", 30), owner);
        driveControls();
        var manual = automation.getControl(target.id(), owner);
        assertThat(manual.priority()).isEqualTo(200);
        assertThat(manual.desiredValue()).isEqualTo("ON");
        var manualDemand = manual.demands().stream().filter(d -> d.priority() == 200).findFirst().orElseThrow();
        automation.releaseManualControl(target.id(), manualDemand.id(), manualDemand.revision(), owner);
        driveControls();
        assertThat(automation.getControl(target.id(), owner).sourceName()).contains("Тихий час");
        assertThat(automation.getControl(target.id(), owner).expiresAt()).isEqualTo(highEnd);

        clock.value = highEnd.toInstant(ZoneOffset.UTC).plusSeconds(1);
        driveControls();
        var restored = automation.getControl(target.id(), owner);
        assertThat(restored.sourceName()).contains("Обычный обдув");
        assertThat(restored.desiredValue()).isEqualTo("ON");
        assertThat(restored.actualConfirmed()).isTrue();
        var lowEnd = restored.expiresAt();
        clock.value = restored.confirmedAt().toInstant(ZoneOffset.UTC).plusSeconds(31);
        driveControls();
        assertThat(automation.getControl(target.id(), owner).expiresAt()).isEqualTo(lowEnd);
        assertThat(automation.getControl(target.id(), owner).sourceName()).contains("Обычный обдув");
        clock.value = lowEnd.toInstant(ZoneOffset.UTC).plusSeconds(1);
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        assertThat(current(target)).isEqualTo("ON");
        assertThat(automation.ruleHistory(low.id(), owner)).hasSize(1);
        assertThat(automation.ruleHistory(high.id(), owner)).hasSize(1);
        verifyNoInteractions(mqtt);
    }

    @Test void holdCoexistsWithClimateAndReturnsChannelToItsCurrentBaseline() {
        var configs = automation.getFarmsOverview(owner).farms().getFirst().greenhouses().stream()
                .filter(g -> g.id().equals(zone.id())).findFirst().orElseThrow().scenarios();
        automation.replaceGreenhouseScenarios(owner, zone.id(), new SaveScenariosRequest(configs.stream()
                .map(c -> new ScenarioConfigRequest(c.scenarioType(), "BOX_CLIMATE".equals(c.scenarioType()), c.config())).toList()));
        automation.evaluateDemoOwner(owner.id());
        driveControls();
        var baseline = automation.getControl(target.id(), owner);
        assertThat(baseline.sourceType()).isEqualTo("scenario");
        assertThat(baseline.desiredValue()).isEqualTo("OFF");

        var definition = hold("Короткое проветривание", "ON", 20, 50, interval(), null);
        assertThat(automation.previewRule(null, definition, owner).conflicts()).isEmpty();
        automation.saveRule(null, new Save(null, true, definition), owner);
        startRules();
        driveControls();
        var holding = automation.getControl(target.id(), owner);
        assertThat(holding.sourceType()).isEqualTo("rule");
        assertThat(holding.actualValue()).isEqualTo("ON");

        clock.value = holding.expiresAt().toInstant(ZoneOffset.UTC).plusSeconds(1);
        automation.evaluateDemoOwner(owner.id());
        driveControls();
        var restored = automation.getControl(target.id(), owner);
        assertThat(restored.sourceType()).isEqualTo("scenario");
        assertThat(restored.desiredValue()).isEqualTo("OFF");
        assertThat(restored.actualValue()).isEqualTo("OFF");
        verifyNoInteractions(mqtt);
    }

    @Test void repeatWhileImmediateRunsOnlyWhileActualSourceIsOnAndStaleAlwaysReleases() {
        var rule = automation.saveRule(null, new Save(null, true,
                hold("Обдув при свете", "ON", 20, 50, repeat("immediate", "release"), condition(30))), owner);
        startRules();
        driveControls();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(1);
        assertThat(automation.getControl(target.id(), owner).demands()).hasSize(1);

        clock.value = START.plusSeconds(10);
        setFact(source, "OFF");
        startRules();
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();

        clock.value = START.plusSeconds(70);
        setFact(source, "ON");
        startRules();
        driveControls();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(2);
        clock.value = START.plusSeconds(101);
        startRules();
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(2);
        verifyNoInteractions(mqtt);
    }

    @Test void equalPriorityWinnerStaysStableAndDoesNotOscillateOnReconciliation() {
        automation.saveRule(null, new Save(null, true, hold("Первый обдув", "ON", 120, 50, interval(), null)), owner);
        automation.saveRule(null, new Save(null, true, hold("Второй обдув", "OFF", 120, 50, interval(), null)), owner);
        startRules();
        driveControls();
        var selected = automation.getControl(target.id(), owner);
        assertThat(selected.demands()).hasSize(2);
        for (int i = 1; i <= 5; i++) {
            clock.value = START.plusSeconds(i);
            driveControls();
            var repeated = automation.getControl(target.id(), owner);
            assertThat(repeated.sourceId()).isEqualTo(selected.sourceId());
            assertThat(repeated.desiredValue()).isEqualTo(selected.desiredValue());
            assertThat(repeated.expiresAt()).isEqualTo(selected.expiresAt());
        }
        verifyNoInteractions(mqtt);
    }

    @Test void blockedCycleThatNeverStartedIsNotReplayedAfterItsPendingLifetime() {
        setFact(target, "ON");
        automation.saveRule(null, new Save(null, true, hold("Устаревший обдув", "ON", 180, 20, interval(), null)), owner);
        automation.saveRule(null, new Save(null, true, hold("Долгий тихий час", "OFF", 120, 80, interval(), null)), owner);
        startRules();
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).hasSize(2);
        clock.value = START.plusSeconds(91);
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).singleElement()
                .satisfies(d -> assertThat(d.sourceName()).contains("Долгий тихий час"));
        clock.value = START.plusSeconds(122);
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        assertThat(current(target)).isEqualTo("OFF");
        verifyNoInteractions(mqtt);
    }

    @Test void afterIntervalWaitsFullOnPeriodAndDoesNotAccumulateMissedCycles() {
        var rule = automation.saveRule(null, new Save(null, true,
                hold("Проветривание", "ON", 20, 50, repeat("after_interval", "release"), condition(180))), owner);
        startRules();
        assertThat(automation.ruleHistory(rule.id(), owner)).isEmpty();
        clock.value = START.plusSeconds(59);
        startRules();
        assertThat(automation.ruleHistory(rule.id(), owner)).isEmpty();
        clock.value = START.plusSeconds(60);
        startRules();
        driveControls();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(1);

        clock.value = START.plusSeconds(65);
        setFact(source, "OFF");
        startRules();
        clock.value = START.plusSeconds(70);
        setFact(source, "ON");
        startRules();
        clock.value = START.plusSeconds(129);
        startRules();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(1);
        clock.value = START.plusSeconds(130);
        startRules();
        driveControls();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(2);

        clock.value = START.plusSeconds(1000);
        setFact(source, "ON");
        startRules();
        driveControls();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSizeLessThanOrEqualTo(3);
        verifyNoInteractions(mqtt);
    }

    @Test void finishKeepsOriginalDeadlineOnOffButMissingTelemetryStillReleasesImmediately() {
        var rule = automation.saveRule(null, new Save(null, true,
                hold("Завершить текущий обдув", "ON", 180, 50, repeat("immediate", "finish"), condition(30))), owner);
        startRules();
        driveControls();
        var deadline = automation.getControl(target.id(), owner).expiresAt();
        clock.value = START.plusSeconds(10);
        setFact(source, "OFF");
        startRules();
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).hasSize(1);
        assertThat(automation.getControl(target.id(), owner).expiresAt()).isEqualTo(deadline);
        clock.value = START.plusSeconds(41);
        startRules();
        driveControls();
        assertThat(automation.getControl(target.id(), owner).demands()).isEmpty();
        assertThat(automation.ruleHistory(rule.id(), owner)).hasSize(1);
        verifyNoInteractions(mqtt);
    }

    @Test void oldExclusiveRulesStillConflictAndHoldCannotControlPump() {
        var legacy = new Definition(2, "Прежний свет", "BOX", zone.id(), interval(), null,
                List.of(json.valueToTree(Map.of("type", "set", "resourceId", target.id(), "value", "ON"))), 60);
        var exclusive = automation.saveRule(null, new Save(null, true, legacy), owner);
        var modern = hold("Новый обдув", "ON", 60, 50, interval(), null);
        assertThat(automation.previewRule(null, modern, owner).conflicts()).isNotEmpty();
        assertThatThrownBy(() -> automation.saveRule(null, new Save(null, true, modern), owner))
                .isInstanceOf(DomainException.class);
        automation.toggleRule(exclusive.id(), new Toggle(exclusive.revision(), false), owner);
        var enabled = automation.saveRule(null, new Save(null, true, modern), owner);
        assertThatThrownBy(() -> automation.saveRule(null, new Save(null, true, legacy), owner))
                .isInstanceOf(DomainException.class);

        var pump = zone.slots().stream().filter(s -> "WATER_PUMP".equals(s.role())).findFirst().orElseThrow();
        var forbidden = new Definition(3, "Небезопасный насос", "BOX", zone.id(), interval(), null,
                List.of(json.valueToTree(Map.of("type", "hold", "resourceId", pump.id(), "value", "ON", "durationSeconds", 60, "priority", 50))), 60);
        assertThatThrownBy(() -> automation.saveRule(null, new Save(null, true, forbidden), owner))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> automation.getRule(enabled.id(), new AuthenticatedUser(-99, "admin")))
                .isInstanceOf(DomainException.class);
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
