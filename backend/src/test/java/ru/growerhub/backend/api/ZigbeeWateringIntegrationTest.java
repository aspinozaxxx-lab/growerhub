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
    @MockBean Clock clock;
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
        doReturn(ZoneOffset.UTC).when(clock).getZone();
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
        assertThat(last.knownVolumeL()).isEqualTo(3.0);
        assertThat(last.volumeSource()).isEqualTo("measured");
        assertThat(pumps.boxStatistics(f.boxId, "day", 10, null, "UTC").measuredVolumeL()).isEqualTo(3.0);
        if (started.boxes().stream().mapToInt(box -> box.plants().size()).sum() > 1) {
            assertThat(last.boxes().stream().flatMap(box -> box.plants().stream())).allMatch(plant -> plant.waterVolumeL() == null);
            assertThat(jdbc.queryForObject("select count(*) from plant_journal_watering_details where pump_session_id=? and water_volume_l is not null",
                    Long.class, started.id())).isZero();
        }
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
            assertThatThrownBy(() -> automation.getWateringPlan(user, f.boxId)).isInstanceOf(DomainException.class);
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void staleStateAndUnsupportedModesDoNotPublishOrCreateSessions() {
        Fixture f = fixture();
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
    void physicalFeatureFlagDoesNotDisableSandboxOrItsStop() {
        Fixture f = fixture();
        start(f, 30);
        automation.evaluateDemoWatering(f.owner.id());
        doReturn(false).when(wateringSettings).enabled();
        assertThatThrownBy(() -> start(f, 30)).isInstanceOf(DomainException.class);
        automation.stopResourceWatering(f.bindingId, f.owner);
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target)).isNull();
        assertThat(start(f, 30).phase()).isEqualTo("starting");
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
        automation.replaceGreenhouseScenarios(f.owner, f.boxId, new AutomationData.SaveScenariosRequest(List.of(
                new AutomationData.ScenarioConfigRequest("WATERING", true, Map.of(
                        "trigger_mode", "schedule", "observe_only", true, "run_seconds", 30,
                        "stop_mode", "fixed_duration")))));
        String publicId = zigbee.getSimulationCoordinator(f.owner.id()).publicId().toString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/automation/greenhouses/" + f.boxId + "/watering-plan").header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.observe_only").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/manual-watering/coordinators/" + publicId + "/devices/" + f.target.ieeeAddress() + "/water-statistics")
                .header("Authorization", bearer))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.supported").value(true));
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
        String otherPublicId = zigbee.getSimulationCoordinator(other.owner.id()).publicId().toString();
        for (String foreignRoute : List.of("/api/automation/greenhouses/" + other.boxId + "/watering-plan",
                "/api/manual-watering/coordinators/" + otherPublicId + "/devices/" + other.target.ieeeAddress() + "/water-statistics")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(foreignRoute)
                    .header("Authorization", bearer))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
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

    @Test
    void unverifiedValveCannotBypassWateringThroughLightAndStillAllowsClosing() throws Exception {
        Fixture f = fixture();
        // Metadata tolko dlya testa zashchity; ne dokazatelstvo sovmestimosti fizicheskogo SWV.
        String metadata = mapper.writeValueAsString(Map.of(
                "software_build_id", "unverified-fixture",
                "definition", Map.of("model", "SWV", "vendor", "SONOFF", "exposes", List.of(
                        Map.of("type", "binary", "property", "state", "access", 3, "value_on", "ON", "value_off", "OFF"),
                        Map.of("type", "composite", "property", "cyclic_timed_irrigation", "access", 3,
                                "features", List.of(Map.of("type", "numeric", "property", "irrigation_duration", "access", 3)))))));
        jdbc.update("update zigbee_device_snapshots set bridge_device_json=? where coordinator_id=? and ieee_address=?",
                metadata, f.target.coordinatorId(), f.target.ieeeAddress());
        var coordinator = zigbee.getSimulationCoordinator(f.owner.id());
        var overview = zigbee.getOverview(f.owner, coordinator.publicId());
        var device = overview.devices().stream().filter(d -> f.target.ieeeAddress().equals(d.ieeeAddress())).findFirst().orElseThrow();
        assertThat(device.watering()).hasSize(1);
        assertThat(device.watering().getFirst().ready()).isFalse();
        assertThat(device.watering().getFirst().maxDurationS()).isNull();
        var response = mapper.readTree(mapper.writeValueAsString(ZigbeeApiMapper.toOverview(overview)));
        var responseDevice = java.util.stream.StreamSupport.stream(response.path("devices").spliterator(), false)
                .filter(d -> f.target.ieeeAddress().equals(d.path("ieee_address").asText())).findFirst().orElseThrow();
        assertThat(responseDevice.path("watering").get(0).path("ready").asBoolean()).isFalse();
        assertThat(responseDevice.path("watering").get(0).path("max_duration_s").isNull()).isTrue();
        for (String role : List.of("LIGHT_SWITCH", "EXHAUST_SWITCH", "AC_SWITCH")) {
            var binding = new AutomationData.ResourceBindingRequest(role, "ZIGBEE_DEVICE", null, null,
                    coordinator.publicId(), f.target.ieeeAddress(), "state", "state", "ON", "OFF");
            assertThatThrownBy(() -> automation.replaceGreenhouseSlots(f.owner, f.boxId,
                    new AutomationData.SaveZoneSlotsRequest(List.of(binding), false)))
                    .isInstanceOf(DomainException.class).hasMessage("Назначьте клапан в слот полива");
        }
        assertThatThrownBy(() -> zigbee.setDeviceProperty(f.owner, coordinator.publicId(), f.target.ieeeAddress(), "state", "ON"))
                .isInstanceOf(DomainException.class).hasMessage("Запускайте клапан через полив с ограничением времени");
        zigbee.setDeviceProperty(f.owner, coordinator.publicId(), f.target.ieeeAddress(), "state", "OFF");
        verifyNoInteractions(publisher);
    }

    @Test
    void coordinatorSnapshotDoesNotBreakDeviceCatalogFarmOrDashboard() {
        Fixture f = fixture();
        jdbc.update("insert into zigbee_device_snapshots (coordinator_id, ieee_address, friendly_name, coordinator, updated_at, bridge_device_json) values (?,?,?,?,?,?)",
                f.target.coordinatorId(), "0x000000000000c001", "Coordinator", true,
                LocalDateTime.ofInstant(time, ZoneOffset.UTC), "{}");
        var coordinator = zigbee.getSimulationCoordinator(f.owner.id());
        var overview = zigbee.getOverview(f.owner, coordinator.publicId());
        var coordinatorDevice = overview.devices().stream().filter(d -> d.coordinator()).findFirst().orElseThrow();
        assertThat(coordinatorDevice.watering()).isEmpty();
        var valve = overview.devices().stream().filter(d -> f.target.ieeeAddress().equals(d.ieeeAddress())).findFirst().orElseThrow();
        assertThat(valve.watering().getFirst().ready()).isTrue();
        var farm = automation.getFarmsOverview(f.owner);
        assertThat(farm.farms().getFirst().greenhouses().getFirst().slots()).anyMatch(slot -> slot.id().equals(f.bindingId));
        assertThat(automation.getOverview(f.owner)).isNotNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void pulseSessionWaitsForClosureAndCountsEachMeterOperationOnlyOnce() {
        Fixture f = fixture();
        var session = automation.startResourceWatering(f.bindingId,
                new AutomationData.ManualWateringStartRequest("timed", 20, null, true, 10, 5), f.owner);
        automation.evaluateDemoWatering(f.owner.id());
        time = time.plusSeconds(10);
        demo.tick(f.space.id());
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("pause");
        assertThat(pumps.currentSession(f.target).activeDurationS()).isEqualTo(10);
        automation.evaluateDemoWatering(f.owner.id());
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        time = time.plusSeconds(5);
        demo.tick(f.space.id());
        automation.evaluateDemoWatering(f.owner.id());
        automation.evaluateDemoWatering(f.owner.id());
        verify(gateway, times(2)).publishWateringStart(anyString(), anyString(), anyMap());
        time = time.plusSeconds(10);
        demo.tick(f.space.id());
        automation.evaluateDemoWatering(f.owner.id());
        var completed = pumps.listSessions(f.target, 10, null).items().getFirst();
        assertThat(completed.id()).isEqualTo(session.id());
        assertThat(completed.phase()).isEqualTo("completed");
        assertThat(completed.activeDurationS()).isEqualTo(20);
        assertThat(completed.knownVolumeL()).isEqualTo(2.0);
        assertThat(completed.volumeSource()).isEqualTo("measured");
        assertThat(journalCount(session.id())).isGreaterThan(0);
        verifyNoInteractions(publisher);
    }

    @Test
    void positiveFlowAfterOffBlocksPauseAndNextPulse() {
        Fixture f = fixture();
        automation.startResourceWatering(f.bindingId,
                new AutomationData.ManualWateringStartRequest("timed", 20, null, true, 10, 5), f.owner);
        automation.evaluateDemoWatering(f.owner.id());
        time = time.plusSeconds(10);
        zigbee.recordSimulatedSnapshot(f.target.coordinatorId(), ZigbeeMqttMessageType.DEVICE_STATE, f.name,
                Map.of("state", "OFF", "flow", 1), LocalDateTime.ofInstant(time, ZoneOffset.UTC));
        automation.evaluateDemoWatering(f.owner.id());
        assertThat(pumps.currentSession(f.target).phase()).isEqualTo("stopping");
        time = time.plusSeconds(6);
        automation.evaluateDemoWatering(f.owner.id());
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        assertThat(pumps.currentSession(f.target).finishedAt()).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void scheduleNeedsNoSoilAndDurableExecutionKeyPreventsSecondStart() {
        Fixture f = fixture();
        var box = automation.getFarmsOverview(f.owner).farms().getFirst().greenhouses().stream().filter(b -> b.id().equals(f.boxId)).findFirst().orElseThrow();
        var slots = box.slots().stream().filter(s -> !"SOIL_MOISTURE_SENSOR".equals(s.role())).map(s -> new AutomationData.ResourceBindingRequest(
                s.role(), s.sourceType(), s.nativeSensorId(), s.nativePumpId(), s.zigbeeCoordinatorId(), s.zigbeeIeeeAddress(),
                s.zigbeeProperty(), s.commandProperty(), s.onValue(), s.offValue())).toList();
        automation.replaceGreenhouseSlots(f.owner, f.boxId, new AutomationData.SaveZoneSlotsRequest(slots, false));
        time = time.plusSeconds(7200);
        demo.authorize(f.space.id(), f.space.generation(), false);
        demo.tick(f.space.id());
        String schedule = time.atZone(java.time.ZoneId.of("UTC")).toLocalTime().withSecond(0).withNano(0).toString();
        var cfg = Map.<String, Object>of("trigger_mode", "schedule", "schedule_time", schedule, "observe_only", false,
                "stop_mode", "fixed_duration", "run_seconds", 10, "min_interval_hours", 1);
        automation.replaceGreenhouseScenarios(f.owner, f.boxId, new AutomationData.SaveScenariosRequest(List.of(
                new AutomationData.ScenarioConfigRequest("WATERING", true, cfg))));
        var plan = automation.getWateringPlan(f.owner, f.boxId);
        assertThat(plan.due()).as("%s", plan).isTrue();
        automation.evaluateDemoOwner(f.owner.id());
        automation.evaluateDemoWatering(f.owner.id());
        var first = pumps.currentSession(f.target);
        assertThat(first).isNotNull();
        time = time.plusSeconds(10);
        demo.tick(f.space.id());
        automation.evaluateDemoWatering(f.owner.id());
        jdbc.update("update automation_scenario_states set runtime_json='{}' where scope_type='BOX' and scope_id=? and scenario_type='WATERING'", f.boxId);
        // Emuliruem otkat runtime posle komandy; sessiya s klyuchom uzhe ustojchiva.
        automation.evaluateDemoOwner(f.owner.id());
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        assertThat(pumps.listSessions(f.target, 10, null).items()).hasSize(1);
        var repeated = pumps.startSession(new PumpSessionData.Start(null, "automation", "timed", 10, null,
                false, null, null, List.of(), null, null).withZigbeeTarget(f.target).withExecutionKey(plan.executionKey()), f.owner);
        assertThat(repeated.id()).isEqualTo(first.id());
        verify(gateway, times(1)).publishWateringStart(anyString(), anyString(), anyMap());
        verifyNoInteractions(publisher);
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
