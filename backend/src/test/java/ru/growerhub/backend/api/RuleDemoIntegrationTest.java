package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.automation.engine.RuleWorker;
import ru.growerhub.backend.common.config.automation.RuleSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.pump.PumpFacade;

@SpringBootTest(properties = {"MQTT_HOST=", "demo.enabled=true", "demo.history-days=1", "demo.history-step-minutes=360",
        "spring.datasource.url=jdbc:h2:mem:rule_demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuleDemoIntegrationTest extends IntegrationTestBase {
    @Autowired DemoFacade demo;
    @Autowired AutomationFacade automation;
    @Autowired PumpFacade pumps;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RuleSettings settings;
    @Autowired MutableClock clock;
    @MockBean MqttPublisher publisher;
    @MockBean ru.growerhub.backend.demo.engine.DemoWorker demoWorker;
    @MockBean ru.growerhub.backend.automation.engine.AutomationWorker automationWorker;
    @MockBean RuleWorker scheduledRules;
    static class MutableClock extends Clock {
        Instant value = Instant.parse("2026-10-19T13:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return value; }
    }
    @org.springframework.boot.test.context.TestConfiguration static class TimeConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock ruleDemoClock() { return new MutableClock(); }
    }
    @Test void fullPulseCycleUsesRealDemoTransportAndPersistentPumpAcrossWorkerRecreation() throws Exception {
        jdbc.update("MERGE INTO demo_capacity (id) KEY(id) VALUES (1)");
        var space = demo.createGuest("ru", "Europe/Moscow", "rule-demo-test");
        var user = new AuthenticatedUser(space.dataUserId(), "demo");
        var farm = automation.getFarmsOverview(user).farms().getFirst();
        var zone = farm.greenhouses().stream().filter(g -> g.slots().stream().anyMatch(s -> s.nativePumpId() != null)).findFirst().orElseThrow();
        var pump = zone.slots().stream().filter(s -> s.nativePumpId() != null).findFirst().orElseThrow();
        var trigger = json.readTree("{\"type\":\"calendar\",\"every\":2,\"unit\":\"day\",\"startDate\":\"2026-10-19\",\"at\":\"16:00\",\"timezone\":\"Europe/Moscow\"}");
        var action = json.valueToTree(Map.of("type", "water", "resourceId", pump.id(), "durationSeconds", 480,
                "pulse", Map.of("runSeconds", 180, "pauseSeconds", 300)));
        var d = new Definition(2, "Полив каждые 2 дня", "BOX", zone.id(), trigger, null, List.of(action), 60);
        automation.replaceGreenhouseScenarios(user, zone.id(), new ru.growerhub.backend.automation.contract.AutomationData.SaveScenariosRequest(
                zone.scenarios().stream().map(s -> new ru.growerhub.backend.automation.contract.AutomationData.ScenarioConfigRequest(s.scenarioType(),
                        s.enabled() && !"WATERING".equals(s.scenarioType()), s.config())).toList()));
        var preview = automation.previewRule(null, d, user);
        assertThat(preview.conflicts()).isEmpty(); assertThat(preview.readiness()).isEmpty();
        assertThat(preview.timing().cycleSeconds()).isEqualTo(1080);
        var rule = automation.saveRule(null, new Save(null, true, d), user);
        var engine = new RuleWorker(automation, settings); Instant first = clock.instant(); engine.tick();
        var run = automation.ruleHistory(rule.id(), user).getFirst();
        String key = "rule:" + run.id() + ":0";
        Long sessionId = automation.ruleWateringExecution(key, user).id();
        boolean sawPause = false;
        for (int second = 1; second <= 1100; second++) {
            clock.value = first.plusSeconds(second);
            if (second % 60 == 0) demo.authorize(space.id(), space.generation(), false);
            demo.tick(space.id()); automation.evaluateDemoWatering(user.id());
            if (second % 5 == 0) engine.tick();
            if (second == 200) {
                assertThat(automation.ruleWateringExecution(key, user).phase()).isEqualTo("pause");
                engine = new RuleWorker(automation, settings); sawPause = true;
            }
        }
        var session = automation.ruleWateringExecution(key, user);
        assertThat(sawPause).isTrue(); assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.phase()).withFailMessage(json.writeValueAsString(session)).isEqualTo("completed"); assertThat(session.completionReason()).isEqualTo("duration");
        assertThat(session.activeDurationS()).isEqualTo(480); assertThat(session.pulseEnabled()).isTrue();
        assertThat(automation.ruleHistory(rule.id(), user)).hasSize(1);
        assertThat(automation.ruleHistory(rule.id(), user).getFirst().status()).isEqualTo("completed");
        assertThat(jdbc.queryForObject("select count(*) from pump_watering_sessions where execution_key=?", Integer.class, key)).isEqualTo(1);
        assertThatThrownBy(() -> automation.ruleWateringExecution(key, new AuthenticatedUser(-1, "user")))
                .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        verifyNoInteractions(publisher);
    }
}
