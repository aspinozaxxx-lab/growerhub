package ru.growerhub.backend.api;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.RuleData.*;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.user.jpa.*;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rules;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleIntegrationTest extends IntegrationTestBase {
    @SpyBean AutomationFacade facade;
    @Autowired TestClock clock;
    @MockBean ru.growerhub.backend.automation.engine.RuleWorker worker;
    @MockBean ZigbeeFacade zigbee;
    @MockBean PlantFacade plants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    AuthenticatedUser user, other;
    UUID coordinator = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-18T09:00:00Z");
    static class TestClock extends Clock {
        volatile Instant value = Instant.parse("2026-10-18T09:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }
    @org.springframework.boot.test.context.TestConfiguration static class TimeConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary TestClock testClock() { return new TestClock(); }
    }
    @BeforeAll void migration() {
        for (String table : List.of("automation_rule_runs", "automation_rule_versions", "automation_rules")) jdbc.execute("drop table " + table);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V35__declarative_rules.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    @BeforeEach void prepare() {
        for (String table : List.of("automation_rule_runs", "automation_rule_versions", "automation_rules")) jdbc.execute("delete from " + table);
        users.deleteAll(); user = account("owner"); other = account("other");
        clock.value = at;
        when(plants.listPlants(any())).thenReturn(List.of());
        doReturn(overview(false, true, "OFF")).when(facade).getFarmsOverview(user);
        doReturn(new FarmsOverview(List.of(), null, List.of(), null)).when(facade).getFarmsOverview(other);
    }
    @Test void restartDuringDelayResumesOnceAndTelemetryDoesNotInvalidateBinding() throws Exception {
        var rule = facade.saveRule(null, new Save(null, true, definition("[{\"type\":\"set\",\"resourceId\":1,\"value\":\"ON\"},{\"type\":\"delay\",\"seconds\":90},{\"type\":\"set\",\"resourceId\":1,\"value\":\"OFF\"}]")), user);
        facade.evaluateRules(user.id()); facade.evaluateRules(user.id());
        assertThat(facade.ruleHistory(rule.id(), user)).hasSize(1);
        var on = facade.claimRuleStep(user.id()); assertThat(on).isNotNull();
        facade.finishRuleStep(on, facade.executeRuleStep(on), true);
        assertThat(facade.claimRuleStep(user.id())).isNull();
        assertThat(facade.claimRuleStep(user.id())).isNull();
        clock.value = at.plusSeconds(91);
        doReturn(overview(false, true, "ON")).when(facade).getFarmsOverview(user);
        var off = facade.claimRuleStep(user.id()); assertThat(off.action().path("value").asText()).isEqualTo("OFF");
        facade.finishRuleStep(off, facade.executeRuleStep(off), true); facade.claimRuleStep(user.id());
        assertThat(facade.ruleHistory(rule.id(), user).getFirst().status()).isEqualTo("completed");
        verify(zigbee).setDeviceProperty(user, coordinator, "0x1", "state", "ON");
        verify(zigbee).setDeviceProperty(user, coordinator, "0x1", "state", "OFF");
        assertThatThrownBy(() -> facade.executeRuleStep(off)).isInstanceOf(DomainException.class);
    }
    @Test void lostAcknowledgementNeverResendsWateringOrSwitchCommand() throws Exception {
        var rule = facade.saveRule(null, new Save(null, true, definition("[{\"type\":\"set\",\"resourceId\":1,\"value\":\"ON\"}]")), user);
        facade.evaluateRules(user.id()); var step = facade.claimRuleStep(user.id());
        facade.executeRuleStep(step);
        clock.value = at.plusSeconds(61);
        assertThat(facade.claimRuleStep(user.id())).isNull();
        assertThat(facade.ruleHistory(rule.id(), user).getFirst().status()).isEqualTo("uncertain");
        assertThatThrownBy(() -> facade.executeRuleStep(step)).isInstanceOf(DomainException.class);
        verify(zigbee, times(1)).setDeviceProperty(any(), any(), any(), any(), any());
    }
    @Test void versionsAndConflictsPreventStaleOrCompetingControl() throws Exception {
        var d = definition("[{\"type\":\"set\",\"resourceId\":1,\"value\":\"ON\"}]");
        doReturn(overview(true, true, "OFF")).when(facade).getFarmsOverview(user);
        assertThat(facade.previewRule(null, d, user).conflicts()).isNotEmpty();
        assertThatThrownBy(() -> facade.saveRule(null, new Save(null, true, d), user)).isInstanceOf(DomainException.class);
        doReturn(overview(false, true, "OFF")).when(facade).getFarmsOverview(user);
        var rule = facade.saveRule(null, new Save(null, true, d), user);
        assertThatThrownBy(() -> facade.saveRule(null, new Save(null, true, d), user)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> facade.getRule(rule.id(), other)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> facade.toggleRule(rule.id(), new Toggle(99, false), user)).isInstanceOf(DomainException.class);
        facade.evaluateRules(user.id());
        var disabled = facade.toggleRule(rule.id(), new Toggle(1, false), user);
        assertThat(facade.ruleHistory(rule.id(), user).getFirst().status()).isEqualTo("cancelled");
        assertThat(facade.claimRuleStep(user.id())).isNull();
        var restored = facade.restoreRule(rule.id(), new Restore(disabled.revision(), 1), user);
        assertThat(restored.enabled()).isFalse(); assertThat(restored.revision()).isEqualTo(3);
        assertThat(facade.ruleVersions(rule.id(), user)).hasSize(3);
        verifyNoInteractions(zigbee);
    }
    @Test void boundedWateringAndReadinessAreCheckedBeforeStarting() throws Exception {
        var d = definition("[{\"type\":\"water\",\"resourceId\":2,\"durationSeconds\":301}]");
        assertThatThrownBy(() -> facade.saveRule(null, new Save(null, true, d), user)).isInstanceOf(DomainException.class);
        var safe = definition("[{\"type\":\"water\",\"resourceId\":2,\"durationSeconds\":300}]");
        var rule = facade.saveRule(null, new Save(null, true, safe), user);
        for (int i = 0; i < 7; i++) {
            clock.value = at.plusSeconds(i * 3600L); facade.evaluateRules(user.id());
            jdbc.update("update automation_rule_runs set status='completed'");
        }
        assertThat(facade.ruleHistory(rule.id(), user)).hasSize(6);
        assertThat(facade.getRule(rule.id(), user).reason()).contains("Суточный лимит");
        doReturn(overview(false, false, "OFF")).when(facade).getFarmsOverview(user);
        assertThat(facade.previewRule(rule.id(), safe, user).readiness()).isNotEmpty();
        verifyNoInteractions(zigbee);
    }
    Definition definition(String actions) throws Exception { return new Definition(1, "Проба", "ROOM", 1, json.readTree("{\"type\":\"interval\",\"seconds\":3600,\"anchor\":\"2026-10-18T09:00:00Z\"}"), null, Arrays.asList(json.readValue(actions, JsonNode[].class)), 60); }
    AuthenticatedUser account(String name) { var now = LocalDateTime.ofInstant(at, ZoneOffset.UTC); var row = users.save(UserEntity.create(name + "@rules.test", name, "user", true, now, now)); return new AuthenticatedUser(row.getId(), "user"); }
    FarmsOverview overview(boolean fixed, boolean ready, String value) {
        var time = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var r = new ResourceBinding(1, "ROOM", 1, "LIGHT_SWITCH", "ZIGBEE", null, null, coordinator, "0x1", "state", "state", "ON", "OFF", value, time, "ONLINE", null, "Свет", ready, null);
        var pump = new ResourceBinding(2, "ROOM", 1, "WATER_PUMP", "NATIVE", null, 1, null, null, null, null, null, null, null, time, "ONLINE", null, "Насос", ready, null);
        var scenarios = fixed ? List.of(new ScenarioConfig(1, "ROOM", 1, "LIGHT_SCHEDULE", true, Map.of(), null, null, null)) : List.<ScenarioConfig>of();
        return new FarmsOverview(List.of(new UserFarm(1, "Ферма", true, null, List.of(r, pump), scenarios, List.of(), List.of(), List.of(), time, time)), null, List.of(), null);
    }
}
