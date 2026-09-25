package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.automation.engine.AutomationWorker;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.contract.DemoData;
import ru.growerhub.backend.demo.engine.DemoWorker;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeWateringData;

@SpringBootTest(properties = {
        "MQTT_HOST=", "SELF_SERVICE_ENABLED=true", "demo.enabled=true", "zigbee.watering.enabled=true",
        "demo.history-days=1", "demo.history-step-minutes=1440", "demo.max-creates-per-hour=100",
        "demo.max-active-spaces=100", "demo.max-guest-spaces=100",
        "spring.datasource.url=jdbc:h2:mem:zigbee_watering_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ZigbeeWateringIntegrationTest extends IntegrationTestBase {
    @Autowired DemoFacade demo;
    @Autowired AutomationFacade automation;
    @Autowired PumpFacade pumps;
    @Autowired ZigbeeFacade zigbee;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired ru.growerhub.backend.auth.AuthFacade auth;
    @SpyBean Clock clock;
    @MockBean MqttPublisher publisher;
    @MockBean DemoWorker demoWorker;
    @MockBean AutomationWorker automationWorker;
    @SpyBean ru.growerhub.backend.mqtt.MqttZigbeeCommandGateway gateway;
    @SpyBean ru.growerhub.backend.common.config.zigbee.ZigbeeWateringSettings wateringSettings;
    private Instant time;

    @BeforeEach
    void setup() {
        time = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        doAnswer(call -> time).when(clock).instant();
        jdbc.update("MERGE INTO demo_capacity (id) KEY(id) VALUES (1)");
        clearInvocations(publisher);
    }

    @Test
    void sharedSessionWaitsForTelemetryAndFinishesOnceWithoutPhysicalCommands() {
        Fixture f = fixture();
        var started = start(f, 30);
        assertThat(started.pumpId()).isNull();
        assertThat(started.executorType()).isEqualTo("ZIGBEE_DEVICE");
        assertThat(started.phase()).isEqualTo("starting");
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("running");
        time = time.plusSeconds(31);
        demo.tick(f.space.id());
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target)).isNull();
        var last = pumps.listSessions(f.target, 10, null).items().getFirst();
        assertThat(last.phase()).isEqualTo("completed");
        assertThat(last.activeDurationS()).isEqualTo(30);
        assertThat(last.knownVolumeL()).isPositive();
        long entries = journalCount(started.id());
        assertThat(entries).isEqualTo(started.boxes().stream().mapToLong(box -> box.plants().size()).sum());
        pumps.advanceSession(started.id(), null, LocalDateTime.ofInstant(time, ZoneOffset.UTC));
        assertThat(journalCount(started.id())).isEqualTo(entries);
        verifyNoInteractions(publisher);
    }

    @Test
    void secondStartAndReassignmentAreRejectedUntilClosureAndHistorySurvivesRebinding() {
        Fixture f = fixture();
        var started = start(f, 30);
        assertThatThrownBy(() -> start(f, 30)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> automation.replaceGreenhouseSlots(f.owner, f.boxId,
                new AutomationData.SaveZoneSlotsRequest(List.of(), false))).isInstanceOf(DomainException.class);
        automation.evaluateDemoWatering(f.owner.id());
        time = time.plusSeconds(3);
        automation.stopResourceWatering(f.bindingId, f.owner);
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target)).isNull();
        assertThat(pumps.listSessions(f.target, 10, null).items().getFirst().id()).isEqualTo(started.id());
        verifyNoInteractions(publisher);
    }

    @Test
    void otherTenantAndAdminCannotUseAnOrdinaryUsersResourceEndpoint() {
        Fixture f = fixture();
        var other = demo.createGuest("ru", "UTC", UUID.randomUUID().toString());
        for (var user : List.of(new AuthenticatedUser(other.dataUserId(), "demo"), new AuthenticatedUser(other.dataUserId(), "admin"))) {
            assertThatThrownBy(() -> automation.startResourceWatering(f.bindingId, request(30), user)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> automation.stopResourceWatering(f.bindingId, user)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> automation.resourceWateringSessions(f.bindingId, 10, null, user)).isInstanceOf(DomainException.class);
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void staleStateAndUnsupportedModesDoNotPublishOrCreateSessions() {
        Fixture f = fixture();
        assertThatThrownBy(() -> automation.startResourceWatering(f.bindingId,
                new AutomationData.ManualWateringStartRequest("timed", 30, null, true, 5, 5), f.owner)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> automation.startResourceWatering(f.bindingId,
                new AutomationData.ManualWateringStartRequest("until_leak", null, 30, false, null, null), f.owner)).isInstanceOf(DomainException.class);
        time = time.plusSeconds(121);
        assertThatThrownBy(() -> start(f, 30)).isInstanceOf(DomainException.class);
        assertThat(pumps.listSessions(f.target, 10, null).items()).isEmpty();
        verifyNoInteractions(publisher);
    }

    @Test
    void offlineStopRemainsPendingUntilFreshClosedStateAndNeverRestarts() {
        Fixture f = fixture();
        var start = start(f, 30);
        automation.evaluateDemoWatering(f.owner.id());
        time = time.plusSeconds(2);
        zigbee.recordSimulatedSnapshot(f.target.coordinatorId(), ZigbeeMqttMessageType.DEVICE_AVAILABILITY,
                f.name, Map.of("state", "offline"), LocalDateTime.ofInstant(time, ZoneOffset.UTC));
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("stopping");
        assertThat(pumps.currentSession(f.target).finishedAt()).isNull();
        time = time.plusSeconds(1);
        demo.tick(f.space.id());
        zigbee.recordSimulatedSnapshot(f.target.coordinatorId(), ZigbeeMqttMessageType.DEVICE_AVAILABILITY,
                f.name, Map.of("state", "online"), LocalDateTime.ofInstant(time, ZoneOffset.UTC));
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.listSessions(f.target, 10, null).items().getFirst().phase()).isEqualTo("failed");
        assertThat(pumps.listSessions(f.target, 10, null).items()).hasSize(1);
        verifyNoInteractions(publisher);
    }

    @Test
    void resetStopsAndDeletesOnlyItsSimulatedValveSessions() {
        Fixture first = fixture();
        Fixture second = fixture();
        start(first, 30);
        var kept = start(second, 30);
        demo.reset(first.space.id(), first.space.generation());
        assertThat(pumps.currentSession(second.target).id()).isEqualTo(kept.id());
        assertThat(jdbc.queryForObject("select count(*) from pump_watering_sessions where zigbee_coordinator_id=?",
                Integer.class, first.target.coordinatorId())).isZero();
        verifyNoInteractions(publisher);
    }

    @Test
    void missingStartConfirmationIsNotWateringAndRetryOnlyCloses() {
        Fixture f = fixture();
        doNothing().when(gateway).publishWateringStart(anyString(), anyString(), anyMap());
        var started = start(f, 60);
        time = time.plusSeconds(16);
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("stopping");
        automation.evaluateDemoWatering(f.owner.id());
        var failed = pumps.listSessions(f.target, 10, null).items().getFirst();
        assertThat(failed.phase()).isEqualTo("failed");
        assertThat(failed.activeDurationS()).isZero();
        assertThat(journalCount(started.id())).isZero();
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        verifyNoInteractions(publisher);
    }

    @Test
    void commandFailureKeepsRecoveryStateAndDoesNotCreateSuccessfulJournal() {
        Fixture f = fixture();
        doThrow(new DomainException("bad_gateway", "Test command rejected"))
                .when(gateway).publishWateringStart(anyString(), anyString(), anyMap());
        assertThatThrownBy(() -> start(f, 30)).isInstanceOf(DomainException.class);
        var current = pumps.currentSession(f.target);
        assertThat(current.phase()).isEqualTo("stopping");
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.listSessions(f.target, 10, null).items().getFirst().phase()).isEqualTo("failed");
        assertThat(journalCount(current.id())).isZero();
        verifyNoInteractions(publisher);
    }

    @Test
    void featureFlagRevocationBlocksNewStartsButKeepsStopAvailable() {
        Fixture f = fixture();
        start(f, 30);
        automation.evaluateDemoWatering(f.owner.id());
        doReturn(false).when(wateringSettings).enabled();
        assertThatThrownBy(() -> start(f, 30)).isInstanceOf(DomainException.class);
        automation.stopResourceWatering(f.bindingId, f.owner);
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target)).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void concurrentRequestsPublishExactlyOneStart() throws Exception {
        Fixture f = fixture();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> action = () -> {
                gate.await();
                try { start(f, 30); return true; }
                catch (DomainException ex) { return false; }
            };
            var a = threads.submit(action);
            var b = threads.submit(action);
            gate.countDown();
            assertThat(List.of(a.get(20, java.util.concurrent.TimeUnit.SECONDS), b.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        assertThat(pumps.listSessions(f.target, 10, null).items()).hasSize(1);
        verifyNoInteractions(publisher);
    }

    @Test
    void ordinaryWateringScenarioStartsTheAssignedValve() {
        Fixture f = fixture();
        var greenhouse = automation.getFarmsOverview(f.owner).farms().getFirst().greenhouses().stream()
                .filter(box -> box.id().equals(f.boxId)).findFirst().orElseThrow();
        var config = new java.util.LinkedHashMap<>(greenhouse.scenarios().stream()
                .filter(s -> "WATERING".equals(s.scenarioType())).findFirst().orElseThrow().config());
        config.put("soil_threshold_percent", 100);
        config.put("min_interval_hours", 0);
        config.put("daily_max_seconds", 3600);
        automation.replaceGreenhouseScenarios(f.owner, f.boxId, new AutomationData.SaveScenariosRequest(
                List.of(new AutomationData.ScenarioConfigRequest("WATERING", true, config))));
        automation.evaluateDemoOwner(f.owner.id());
        assertThat(pumps.currentSession(f.target)).isNotNull();
        assertThat(pumps.currentSession(f.target).source()).isEqualTo("automation");
        verifyNoInteractions(publisher);
    }

    @Test
    void leakStopsValveAndRepeatedWorkerPassDoesNotRestartIt() {
        Fixture f = fixture();
        var started = start(f, 60);
        automation.evaluateDemoWatering(f.owner.id());
        time = time.plusSeconds(3);
        pumps.advanceSession(started.id(), new PumpSessionData.LeakProbe(true, true,
                List.of(new PumpSessionData.LeakState("test", true, true))), LocalDateTime.now(clock));
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("stopping");
        automation.evaluateDemoWatering(f.owner.id());
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.listSessions(f.target, 10, null).items().getFirst().completionReason()).isEqualTo("leak");
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        verifyNoInteractions(publisher);
    }

    @Test
    void resourceEndpointsAllowDemoAndRejectForeignBindings() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Origin", "https://growerhub.ru");
        request.setRemoteAddr("198.51.100.17");
        var tokens = auth.startDemo(null, "ru", "UTC", request, new org.springframework.mock.web.MockHttpServletResponse());
        Fixture f = fixture(tokens.space());
        String route = "/api/manual-watering/resources/" + f.bindingId;
        String bearer = "Bearer " + tokens.accessToken();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route + "/start")
                .header("Authorization", bearer).contentType("application/json")
                .content("{\"mode\":\"timed\",\"duration_s\":30}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.phase").value("starting"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route + "/sessions")
                .header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(route + "/stop")
                .header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var other = fixture();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/manual-watering/resources/" + other.bindingId + "/start")
                .header("Authorization", bearer).contentType("application/json").content("{\"duration_s\":30}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        verifyNoInteractions(publisher);
    }

    private Fixture fixture() {
        var space = demo.createGuest("ru", "UTC", UUID.randomUUID().toString());
        return fixture(space);
    }

    private Fixture fixture(DemoData.Space space) {
        var owner = new AuthenticatedUser(space.dataUserId(), "demo");
        var valve = demo.addDevice(owner, new DemoData.AddDevice("valve", "Проверяемый клапан"));
        var farm = automation.getFarmsOverview(owner);
        var box = farm.farms().getFirst().greenhouses().getFirst();
        var catalog = farm.resourceCatalog().zigbeeDevices().stream()
                .filter(device -> "Проверяемый клапан".equals(device.friendlyName())).findFirst().orElseThrow();
        var slots = new ArrayList<AutomationData.ResourceBindingRequest>();
        for (var slot : box.slots()) if (!"WATER_PUMP".equals(slot.role())) slots.add(new AutomationData.ResourceBindingRequest(
                slot.role(), slot.sourceType(), slot.nativeSensorId(), slot.nativePumpId(), slot.zigbeeCoordinatorId(),
                slot.zigbeeIeeeAddress(), slot.zigbeeProperty(), slot.commandProperty(), slot.onValue(), slot.offValue()));
        slots.add(new AutomationData.ResourceBindingRequest("WATER_PUMP", "ZIGBEE_DEVICE", null, null,
                catalog.coordinatorId(), catalog.ieeeAddress(), "state", "state", "ON", "OFF"));
        automation.replaceGreenhouseSlots(owner, box.id(), new AutomationData.SaveZoneSlotsRequest(slots, false));
        var assigned = automation.getFarmsOverview(owner).farms().getFirst().greenhouses().getFirst().slots()
                .stream().filter(slot -> "WATER_PUMP".equals(slot.role())).findFirst().orElseThrow();
        var coordinator = zigbee.getSimulationCoordinator(owner.id());
        return new Fixture(space, owner, box.id(), assigned.id(), "Проверяемый клапан",
                new ZigbeeWateringData.Target(coordinator.id(), catalog.ieeeAddress(), "state"));
    }

    private PumpSessionData.View start(Fixture f, int duration) { return automation.startResourceWatering(f.bindingId, request(duration), f.owner); }
    private AutomationData.ManualWateringStartRequest request(int seconds) {
        return new AutomationData.ManualWateringStartRequest("timed", seconds, null, false, null, null);
    }
    private long journalCount(Long sessionId) {
        return jdbc.queryForObject("select count(*) from plant_journal_watering_details where pump_session_id=?", Long.class, sessionId);
    }
    private record Fixture(DemoData.Space space, AuthenticatedUser owner, Integer boxId, Integer bindingId,
                           String name, ZigbeeWateringData.Target target) {}
}
