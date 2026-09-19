package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.auth.engine.JwtService;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.engine.AutomationWorker;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.engine.DemoWorker;
import ru.growerhub.backend.demo.jpa.DemoSpaceRepository;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.mqtt.*;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "MQTT_HOST=", "SELF_SERVICE_ENABLED=true", "demo.enabled=true", "demo.secure-cookie=false",
        "demo.history-days=7", "demo.history-step-minutes=360", "demo.max-creates-per-hour=100",
        "demo.max-active-spaces=100", "demo.max-guest-spaces=100", "demo.allowed-origin=https://growerhub.ru",
        "spring.datasource.url=jdbc:h2:mem:demo_farm_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoFarmIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @SpyBean ru.growerhub.backend.common.config.DemoSettings demoSettings;
    @Autowired ru.growerhub.backend.auth.AuthFacade authFacade;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired UserFacade userFacade;
    @Autowired DeviceRepository devices;
    @Autowired DeviceFacade deviceFacade;
    @Autowired DemoFacade demo;
    @Autowired DemoSpaceRepository spaces;
    @Autowired AutomationFacade automation;
    @Autowired ZigbeeFacade zigbee;
    @Autowired PumpFacade pumps;
    @Autowired ru.growerhub.backend.plant.PlantFacade plantFacade;
    @Autowired ObjectMapper mapper;
    @Autowired MqttSettings mqttSettings;
    @Autowired DebugSettings debugSettings;
    @Autowired MqttMessageLog messageLog;
    @MockBean MqttPublisher publisher;
    @MockBean DemoWorker demoWorker;
    @MockBean AutomationWorker automationWorker;

    @BeforeEach
    void setUp() {
        jdbc.update("MERGE INTO demo_capacity (id) KEY(id) VALUES (1)");
        clearInvocations(publisher);
    }

    @Test
    void seededFarmUsesSharedApplicationHistoryAndVirtualCommands() {
        Response session = start();
        String token = session.path("access_token");
        UUID id = UUID.fromString(session.path("space.id"));
        var space = spaces.findById(id).orElseThrow();
        AuthenticatedUser owner = new AuthenticatedUser(space.dataUserId, "demo");
        var overview = automation.getFarmsOverview(owner);
        assertThat(overview.farms()).hasSize(1);
        assertThat(overview.farms().getFirst().greenhouses()).hasSize(4);
        assertThat(overview.resourceCatalog().plants()).hasSize(6);
        assertThat(overview.resourceCatalog().nativeDevices()).hasSize(4);
        assertThat(overview.resourceCatalog().zigbeeDevices()).hasSize(13);
        assertThat(jdbc.queryForObject("select count(*) from pump_watering_sessions where user_id = ? and finished_at is not null", Integer.class, space.dataUserId)).isEqualTo(28);
        assertThat(jdbc.queryForObject("select count(*) from sensor_readings r join sensors s on s.id=r.sensor_id join devices d on d.id=s.device_id where d.user_id=?", Integer.class, space.dataUserId)).isPositive();
        for (var greenhouse : overview.farms().getFirst().greenhouses()) {
            assertThat(greenhouse.slots()).allMatch(slot -> slot.ready());
            assertThat(greenhouse.scenarios()).hasSize(3).allMatch(scenario -> scenario.enabled() && scenario.readiness().ready());
        }
        int pumpId = overview.resourceCatalog().nativeDevices().getFirst().pumps().getFirst().id();
        request(token).body(Map.of("mode", "timed", "duration_s", 30, "pulse_enabled", false))
                .post("/api/manual-watering/pumps/" + pumpId + "/start").then().statusCode(200).body("phase", equalTo("running"));
        request(token).post("/api/manual-watering/pumps/" + pumpId + "/stop").then().statusCode(200);
        automation.evaluateDemoWatering(space.dataUserId);
        assertThat(pumps.currentSession(pumpId)).isNull();
        Response status = request(token).get("/api/demo/status");
        assertThat(((Number) status.path("devices.find { it.profile == 'light' }.state.energy")).doubleValue()).isPositive();
        String nativeId = status.path("devices.find { it.profile == 'controller' }.id");
        request(token).body(Map.of("device_id", nativeId, "temperature", 34)).post("/api/demo/environment").then().statusCode(200);
        automation.evaluateDemoOwner(space.dataUserId);
        assertThat(automation.getFarmsOverview(owner).farms().getFirst().greenhouses().stream()
                .flatMap(greenhouse -> greenhouse.slots().stream()).filter(slot -> "EXHAUST_SWITCH".equals(slot.role()))
                .anyMatch(slot -> "ON".equals(slot.currentValue()))).isTrue();
        jdbc.update("update demo_devices set updated_at = ? where space_id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), id);
        demo.tick(id);
        assertThat(((Number) request(token).get("/api/demo/status").path("devices.find { it.profile == 'fan' && it.state.state == 'ON' }.state.energy")).doubleValue()).isPositive();
        request(token).body(Map.of("profile", "air", "name", "Дополнительный датчик"))
                .post("/api/demo/devices").then().statusCode(200).body("profile", equalTo("air"));
        verifyNoInteractions(publisher);
    }

    @Test
    void hourlySeedKeepsFullHistoryAndMatchingCurrentStates() throws Exception {
        doReturn(60).when(demoSettings).historyStepMinutes();
        Response session = start();
        UUID id = UUID.fromString(session.path("space.id"));
        var space = spaces.findById(id).orElseThrow();
        var all = jdbc.queryForList("select * from demo_devices where space_id = ?", id);
        assertThat(all).hasSize(17);
        for (var device : all) {
            Map<String, Object> state = mapper.readValue(device.get("state_json").toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(((java.sql.Timestamp) device.get("updated_at")).toLocalDateTime()).isEqualTo(space.createdAt);
            if (device.get("native_device_id") != null) {
                var nativeDevice = devices.findById(((Number) device.get("native_device_id")).intValue()).orElseThrow();
                assertThat(nativeDevice.getLastSeen()).isEqualTo(space.createdAt);
                assertThat(jdbc.queryForObject("select updated_at from device_state_last where device_id = ?",
                        LocalDateTime.class, nativeDevice.getDeviceId())).isEqualTo(space.createdAt);
                for (String type : List.of("AIR_TEMPERATURE", "AIR_HUMIDITY", "SOIL_MOISTURE")) {
                    var readings = new TreeMap<LocalDateTime, Double>();
                    jdbc.query("select r.ts, r.value_numeric from sensor_readings r join sensors s on s.id = r.sensor_id where s.device_id = ? and s.type = ? order by r.ts, r.id",
                            (org.springframework.jdbc.core.RowCallbackHandler) row -> readings.put(row.getTimestamp(1).toLocalDateTime(), row.getDouble(2)), device.get("native_device_id"), type);
                    assertThat(readings).hasSize(169);
                    assertThat(readings.firstKey()).isEqualTo(space.createdAt.minusDays(7));
                    assertThat(readings.lastKey()).isEqualTo(space.createdAt);
                    String metric = switch (type) {
                        case "AIR_TEMPERATURE" -> "temperature";
                        case "AIR_HUMIDITY" -> "humidity";
                        default -> "moisture";
                    };
                    double value = ((Number) state.get(metric)).doubleValue();
                    assertThat(readings.lastEntry().getValue()).isEqualTo("moisture".equals(metric) ? (double) Math.round(value) : value);
                }
            } else {
                var snapshot = jdbc.queryForMap("select availability, last_state_at from zigbee_device_snapshots where coordinator_id = ? and friendly_name = ?",
                        device.get("coordinator_id"), state.get("friendly_name"));
                assertThat(snapshot.get("availability")).isEqualTo("online");
                assertThat(((java.sql.Timestamp) snapshot.get("last_state_at")).toLocalDateTime()).isEqualTo(space.createdAt);
                if ("light".equals(device.get("profile_key"))) {
                    var energy = jdbc.queryForList("select value_numeric from zigbee_device_property_readings where coordinator_id = ? and friendly_name = ? and property = 'energy' order by ts, id",
                            Double.class, device.get("coordinator_id"), state.get("friendly_name"));
                    assertThat(energy).hasSizeGreaterThan(160).isSorted();
                    assertThat(energy.getLast()).isEqualTo(((Number) state.get("energy")).doubleValue()).isPositive();
                }
            }
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void historicalSeedDoesNotRewindDeviceOrInterruptCurrentWatering() {
        Response session = start();
        String token = session.path("access_token");
        var space = spaces.findById(UUID.fromString(session.path("space.id"))).orElseThrow();
        var target = automation.getFarmsOverview(new AuthenticatedUser(space.dataUserId, "demo"))
                .resourceCatalog().nativeDevices().getFirst();
        var device = devices.findById(target.id()).orElseThrow();
        int pumpId = target.pumps().getFirst().id();
        request(token).body(Map.of("mode", "timed", "duration_s", 30, "pulse_enabled", false))
                .post("/api/manual-watering/pumps/" + pumpId + "/start").then().statusCode(200).body("phase", equalTo("running"));
        device = devices.findById(target.id()).orElseThrow();
        var shadow = deviceFacade.getShadowState(device.getDeviceId());
        var lastState = jdbc.queryForMap("select state_json, updated_at from device_state_last where device_id = ?", device.getDeviceId());
        Long wateringId = pumps.currentSession(pumpId).id();
        LocalDateTime at = space.createdAt.minusHours(2).minusMinutes(7);
        var historical = new DeviceShadowState(null, "historical-firmware", null, 55.0, 9.0, 44.0,
                null, null, new DeviceShadowState.RelayState("off"), new DeviceShadowState.RelayState("off"), null);

        deviceFacade.seedSimulatedHistory(device.getDeviceId(), historical, at);

        assertThat(deviceFacade.getShadowState(device.getDeviceId())).isEqualTo(shadow);
        assertThat(jdbc.queryForMap("select state_json, updated_at from device_state_last where device_id = ?", device.getDeviceId())).isEqualTo(lastState);
        var unchanged = devices.findById(device.getId()).orElseThrow();
        assertThat(unchanged.getLastSeen()).isEqualTo(device.getLastSeen());
        assertThat(unchanged.getCurrentVersion()).isEqualTo(device.getCurrentVersion());
        assertThat(pumps.currentSession(pumpId).id()).isEqualTo(wateringId);
        assertThat(pumps.currentSession(pumpId).phase()).isEqualTo("running");
        assertThat(jdbc.queryForObject("select r.value_numeric from sensor_readings r join sensors s on s.id=r.sensor_id where s.device_id=? and s.type='AIR_TEMPERATURE' and r.ts=?",
                Double.class, device.getId(), at)).isEqualTo(9.0);
        request(token).post("/api/manual-watering/pumps/" + pumpId + "/stop").then().statusCode(200);
        verifyNoInteractions(publisher);
    }

    @Test
    void demoCannotReachAnotherFarmCredentialsOrPhysicalTransport() {
        var account = account();
        var physical = DeviceEntity.create(); physical.setDeviceId("PHYSICAL_" + UUID.randomUUID().toString().replace("-", ""));
        physical.setUserId(account.getId()); physical.setName("Рабочая ферма"); physical = devices.saveAndFlush(physical);
        Response session = start(); String token = session.path("access_token");
        var space = spaces.findById(UUID.fromString(session.path("space.id"))).orElseThrow();
        var simulated = devices.findAllByUserId(space.dataUserId).getFirst();
        for (String path : List.of("/api/auth/me", "/api/admin/users")) request(token).get(path).then().statusCode(403);
        request(token).body(Map.of("device_id", physical.getDeviceId())).post("/api/devices/claim").then().statusCode(403);
        request(token).get("/api/device/" + physical.getDeviceId() + "/settings").then().statusCode(anyOf(is(403), is(404)));
        Response other = start(); String otherToken = other.path("access_token");
        int plantId = request(token).get("/api/plants").path("[0].id");
        request(otherToken).get("/api/plants/" + plantId).then().statusCode(404);
        assertThatThrownBy(() -> deviceFacade.adminAssign(simulated.getId(), account.getId())).isInstanceOf(DomainException.class);
        int physicalId = physical.getId();
        assertThatThrownBy(() -> deviceFacade.adminAssign(physicalId, space.dataUserId)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> deviceFacade.seedSimulatedHistory(devices.findById(physicalId).orElseThrow().getDeviceId(), null,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> deviceFacade.provisionMqttDevice(simulated.getDeviceId(), false)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> deviceFacade.handleState(simulated.getDeviceId(), null, LocalDateTime.now(ZoneOffset.UTC))).isInstanceOf(DomainException.class);
        var finalTransport = new PahoMqttPublisher(mqttSettings, debugSettings, mapper, messageLog, deviceFacade, zigbee);
        assertThatThrownBy(() -> finalTransport.publishCmd(simulated.getDeviceId(), Map.of("cmd", "reboot"))).isInstanceOf(DomainException.class);
        var coordinator = zigbee.getSimulationCoordinator(space.dataUserId);
        assertThatThrownBy(() -> finalTransport.publishJson(coordinator.baseTopic() + "/switch/set", Map.of("state", "ON"), 1, false)).isInstanceOf(DomainException.class);
        automation.evaluateAll(); automation.evaluateActiveWateringSessions();
        var unchanged = devices.findById(physicalId).orElseThrow();
        assertThat(unchanged.getUserId()).isEqualTo(account.getId()); assertThat(unchanged.getName()).isEqualTo("Рабочая ферма");
        assertThat(unchanged.isSimulated()).isFalse(); assertThat(unchanged.getLastSeen()).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    void guestCanSaveResetAndResumeWithoutChangingRealAccountData() {
        var account = account(); String accountToken = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        Response guest = start(); String guestToken = guest.path("access_token"); String cookie = guest.cookie("gh_demo_refresh");
        String spaceId = guest.path("space.id");
        request(guestToken).body(Map.of("name", "Моя пробная ферма")).post("/api/automation/farms").then().statusCode(200);
        Response saved = request(accountToken).cookie("gh_demo_refresh", cookie).body(Map.of("replace", false)).post("/api/demo/save");
        saved.then().statusCode(200).body("space.saved", equalTo(true)).body("space.id", equalTo(spaceId));
        String savedCookie = saved.cookie("gh_demo_refresh"); String savedToken = saved.path("access_token");
        request(guestToken).get("/api/automation/farms").then().statusCode(401);
        request(accountToken).get("/api/automation/farms").then().statusCode(200).body("farms", hasSize(0));
        request(null).cookie("gh_demo_refresh", savedCookie).post("/api/demo/refresh").then().statusCode(401);
        request(accountToken).cookie("gh_demo_refresh", savedCookie).post("/api/demo/refresh").then().statusCode(200);
        request(savedToken).get("/api/automation/farms").then().statusCode(200).body("farms", hasSize(2));
        Response reset = request(savedToken).cookie("gh_demo_refresh", savedCookie).post("/api/demo/reset");
        reset.then().statusCode(200).body("space.saved", equalTo(true));
        request(savedToken).get("/api/automation/farms").then().statusCode(401);
        request(reset.path("access_token")).get("/api/automation/farms").then().statusCode(200).body("farms", hasSize(1));
        assertThat(users.findById(account.getId()).orElseThrow().getEmail()).isEqualTo(account.getEmail());
        assertThat(userFacade.getDemoOwnerIds()).contains(spaces.findById(UUID.fromString(spaceId)).orElseThrow().dataUserId);
        request(accountToken).cookie("gh_demo_refresh", reset.cookie("gh_demo_refresh"))
                .post("/api/auth/logout").then().statusCode(200);
        request(reset.path("access_token")).get("/api/demo/status").then().statusCode(401);
        request(accountToken).cookie("gh_demo_refresh", reset.cookie("gh_demo_refresh"))
                .post("/api/demo/refresh").then().statusCode(401);
        verifyNoInteractions(publisher);
    }

    @Test
    void unavailableDemoCookieOnSaveDoesNotInvalidateAccount() {
        var account = account();
        String token = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        request(token).body(Map.of("replace", false)).post("/api/demo/save").then().statusCode(410);
        request(token).cookie("gh_demo_refresh", "unknown-demo-session").body(Map.of("replace", false))
                .post("/api/demo/save").then().statusCode(410);
        request(token).get("/api/auth/me").then().statusCode(200).body("id", equalTo(account.getId()));
        request(token).post("/api/demo/refresh").then().statusCode(401);
        request(null).body(Map.of("replace", false)).post("/api/demo/save").then().statusCode(401);
        request("invalid-account-token").body(Map.of("replace", false)).post("/api/demo/save").then().statusCode(401);
        assertThat(jdbc.queryForObject("select count(*) from demo_spaces where account_user_id = ?", Integer.class, account.getId())).isZero();
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"session_expired", "space_expired", "generation_changed", "space_removed"})
    void unavailableGuestOnSaveReturnsGoneWithoutChangingAccount(String condition) {
        var account = account();
        String token = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        Response guest = start();
        UUID spaceId = UUID.fromString(guest.path("space.id"));
        switch (condition) {
            case "session_expired" -> jdbc.update("update auth_demo_sessions set expires_at = ? where space_id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), spaceId);
            case "space_expired" -> jdbc.update("update demo_spaces set expires_at = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), spaceId);
            case "generation_changed" -> jdbc.update("update demo_spaces set generation = generation + 1 where id = ?", spaceId);
            case "space_removed" -> {
                jdbc.update("update demo_spaces set expires_at = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), spaceId);
                demo.cleanup();
            }
            default -> throw new IllegalArgumentException(condition);
        }
        request(token).cookie("gh_demo_refresh", guest.cookie("gh_demo_refresh")).body(Map.of("replace", false))
                .post("/api/demo/save").then().statusCode(410);
        request(token).get("/api/auth/me").then().statusCode(200).body("id", equalTo(account.getId()));
        request(token).cookie("gh_demo_refresh", guest.cookie("gh_demo_refresh"))
                .post("/api/demo/refresh").then().statusCode(401);
        assertThat(jdbc.queryForObject("select count(*) from demo_spaces where account_user_id = ?", Integer.class, account.getId())).isZero();
        if ("space_removed".equals(condition)) {
            assertThat(spaces.findById(spaceId)).isEmpty();
            assertThatThrownBy(() -> demo.save(spaceId, 1, account.getId(), false))
                    .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.getCode()).isEqualTo("demo_session_unavailable"));
        } else {
            assertThat(spaces.findById(spaceId).orElseThrow().accountUserId).isNull();
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void savingStillRejectsAnotherAccountAndRequiresExplicitReplacement() {
        var account = account();
        var other = account();
        String token = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        String otherToken = jwt.createToken(Map.of("user_id", other.getId()), Duration.ofHours(1));
        Response saved = request(token).body(Map.of("locale", "ru", "timezone", "UTC")).post("/api/demo/start");
        saved.then().statusCode(200);
        UUID savedId = UUID.fromString(saved.path("space.id"));
        for (boolean replace : List.of(false, true)) {
            request(otherToken).cookie("gh_demo_refresh", saved.cookie("gh_demo_refresh")).body(Map.of("replace", replace))
                    .post("/api/demo/save").then().statusCode(401);
        }
        var savedSpace = spaces.findById(savedId).orElseThrow();
        assertThatThrownBy(() -> demo.save(savedId, savedSpace.generation, other.getId(), true))
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.getCode()).isEqualTo("unauthorized"));
        Response guest = start();
        request(guest.path("access_token")).cookie("gh_demo_refresh", guest.cookie("gh_demo_refresh"))
                .body(Map.of("replace", false)).post("/api/demo/save").then().statusCode(403);
        request(token).cookie("gh_demo_refresh", guest.cookie("gh_demo_refresh")).body(Map.of("replace", false))
                .post("/api/demo/save").then().statusCode(409);
        assertThat(spaces.findById(savedId).orElseThrow().accountUserId).isEqualTo(account.getId());
        assertThat(jdbc.queryForObject("select count(*) from demo_spaces where account_user_id = ?", Integer.class, other.getId())).isZero();
        request(token).cookie("gh_demo_refresh", saved.cookie("gh_demo_refresh"))
                .post("/api/demo/refresh").then().statusCode(200);
        verifyNoInteractions(publisher);
    }

    @Test
    void inactiveAndExpiredGuestsAreCleanedWhileSavedFarmRemains() {
        Response guest = start(); String guestId = guest.path("space.id");
        int guestOwner = spaces.findById(UUID.fromString(guestId)).orElseThrow().dataUserId;
        var account = account(); String primary = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        Response saved = request(primary).body(Map.of("locale", "en", "timezone", "UTC")).post("/api/demo/start");
        saved.then().statusCode(200).body("space.saved", equalTo(true));
        UUID savedId = UUID.fromString(saved.path("space.id"));
        var savedSpace = spaces.findById(savedId).orElseThrow();
        var owner = new AuthenticatedUser(savedSpace.dataUserId, "demo");
        int pump = automation.getFarmsOverview(owner).resourceCatalog().nativeDevices().getFirst().pumps().getFirst().id();
        request(saved.path("access_token")).body(Map.of("mode", "timed", "duration_s", 30))
                .post("/api/manual-watering/pumps/" + pump + "/start").then().statusCode(200);
        jdbc.update("update demo_spaces set expires_at = ?, last_active_at = ? where id = ?", LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(2), UUID.fromString(guestId));
        jdbc.update("update demo_spaces set last_active_at = ? where id = ?", LocalDateTime.now().minusDays(1), savedId);
        demo.cleanup();
        assertThat(spaces.findById(UUID.fromString(guestId))).isEmpty();
        assertThat(users.findById(guestOwner)).isEmpty();
        assertThat(spaces.findById(savedId).orElseThrow().paused).isTrue();
        assertThat(pumps.currentSession(pump)).isNull();
        request(primary).cookie("gh_demo_refresh", saved.cookie("gh_demo_refresh")).post("/api/demo/refresh").then().statusCode(200);
        assertThat(spaces.findById(savedId).orElseThrow().paused).isFalse();
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returningVisitorSeesReadyDevicesBeforeWorkerRuns(boolean saved) {
        String primary = saved ? jwt.createToken(Map.of("user_id", account().getId()), Duration.ofHours(1)) : null;
        Response session = request(primary).body(Map.of("locale", "ru", "timezone", "UTC")).post("/api/demo/start");
        session.then().statusCode(200);
        UUID id = UUID.fromString(session.path("space.id"));
        var space = spaces.findById(id).orElseThrow();
        var owner = new AuthenticatedUser(space.dataUserId, "demo");
        var coordinator = zigbee.getSimulationCoordinator(space.dataUserId);
        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        jdbc.update("update demo_spaces set last_active_at = ? where id = ?", stale, id);
        demo.cleanup();
        assertThat(spaces.findById(id).orElseThrow().paused).isTrue();
        var pausedStates = jdbc.queryForList("select state_json from demo_devices where space_id = ? order by id", String.class, id);
        for (var device : deviceFacade.listMyDevices(space.dataUserId)) {
            deviceFacade.handleSimulatedState(device.deviceId(), deviceFacade.getShadowState(device.deviceId()), stale);
        }
        jdbc.update("update zigbee_device_snapshots set last_state_at = ?, updated_at = ? where coordinator_id = ?",
                stale, stale, coordinator.id());
        assertThat(deviceFacade.listMyDevices(space.dataUserId)).hasSize(4)
                .allMatch(device -> !Boolean.TRUE.equals(device.isOnline()));

        LocalDateTime returningAt = LocalDateTime.now(ZoneOffset.UTC);
        request(primary).cookie("gh_demo_refresh", session.cookie("gh_demo_refresh"))
                .body(Map.of("locale", "ru", "timezone", "UTC")).post("/api/demo/start")
                .then().statusCode(200).body("space.id", equalTo(id.toString()));

        assertThat(deviceFacade.listMyDevices(space.dataUserId)).hasSize(4).allSatisfy(device -> {
            assertThat(device.isOnline()).isTrue();
            assertThat(device.lastSeen()).isAfterOrEqualTo(returningAt);
        });
        assertThat(zigbee.getOverview(owner, coordinator.publicId()).devices()).hasSize(13).allSatisfy(device -> {
            assertThat(device.availability()).isEqualTo("online");
            assertThat(device.lastStateAt()).isAfterOrEqualTo(returningAt);
        });
        for (var greenhouse : automation.getFarmsOverview(owner).farms().getFirst().greenhouses()) {
            assertThat(greenhouse.slots()).allMatch(slot -> slot.ready());
        }
        assertThat(jdbc.queryForList("select state_json from demo_devices where space_id = ? order by id", String.class, id))
                .isEqualTo(pausedStates);
        assertThat(spaces.findById(id).orElseThrow().paused).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void creationQuotaIsSerializedAndCrossOriginAdmissionIsDenied() throws Exception {
        long before = spaces.count();
        given().baseUri("http://localhost").port(port).header("Origin", "https://outside.example")
                .contentType("application/json").body(Map.of()).post("/api/demo/start").then().statusCode(403);
        assertThat(spaces.count()).isEqualTo(before);
        Response session = start();
        var space = spaces.findById(UUID.fromString(session.path("space.id"))).orElseThrow();
        var owner = new AuthenticatedUser(space.dataUserId, "demo");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var operations = new ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int i = 0; i < 22; i++) {
                operations.add(() -> {
                    try {
                        plantFacade.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Test plant", null, null, null, null), owner);
                        return true;
                    } catch (DomainException limit) { assertThat(limit.getCode()).isEqualTo("conflict"); return false; }
                });
            }
            int created = 0;
            for (var future : executor.invokeAll(operations)) if (future.get()) created++;
            assertThat(created).isEqualTo(18);
        }
        assertThat(plantFacade.listPlants(owner)).hasSize(24);
        assertThat(userFacade.getAuthUser(space.dataUserId)).isNull();
        verifyNoInteractions(publisher);
    }


    @Test
    void mutationAndResetLimitsLeaveReadingAndStoredFarmAvailable() {
        Response session = start();
        String token = session.path("access_token");
        UUID id = UUID.fromString(session.path("space.id"));
        String controller = request(token).get("/api/demo/status").path("devices.find { it.profile == 'controller' }.id");
        jdbc.update("update demo_spaces set action_window_started_at = ?, actions_in_window = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC), demoSettings.maxActionsPerMinute(), id);
        request(token).body(Map.of("device_id", controller, "temperature", 34))
                .post("/api/demo/environment").then().statusCode(429);
        request(token).get("/api/automation/farms").then().statusCode(200);
        jdbc.update("update demo_spaces set action_window_started_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2), id);
        request(token).body(Map.of("device_id", controller, "temperature", 34))
                .post("/api/demo/environment").then().statusCode(200);
        var previous = spaces.findById(id).orElseThrow();
        jdbc.update("update demo_spaces set reset_window_started_at = ?, resets_in_window = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC), demoSettings.maxResetsPerHour(), id);
        request(token).cookie("gh_demo_refresh", session.cookie("gh_demo_refresh"))
                .post("/api/demo/reset").then().statusCode(429);
        assertThat(spaces.findById(id).orElseThrow().generation).isEqualTo(previous.generation);
        request(token).get("/api/demo/status").then().statusCode(200).body("devices.size()", equalTo(17));
        verifyNoInteractions(publisher);
    }

    @Test
    void sharedScenarioSwitchPreservesDemoSettingsAndOtherOwners() {
        Response guest = start();
        String token = guest.path("access_token");
        var space = spaces.findById(UUID.fromString(guest.path("space.id"))).orElseThrow();
        var owner = new AuthenticatedUser(space.dataUserId, "demo");
        Response other = start();
        var otherSpace = spaces.findById(UUID.fromString(other.path("space.id"))).orElseThrow();
        var otherOwner = new AuthenticatedUser(otherSpace.dataUserId, "demo");
        var settings = automation.getFarmsOverview(owner).farms().stream()
                .flatMap(farm -> farm.greenhouses().stream()).flatMap(greenhouse -> greenhouse.scenarios().stream())
                .map(scenario -> scenario.config()).toList();
        var account = account();
        String primary = jwt.createToken(Map.of("user_id", account.getId()), Duration.ofHours(1));
        request(primary).body(Map.of("name", "Physical farm")).post("/api/automation/farms").then().statusCode(200);
        String realBefore = request(primary).get("/api/automation/farms").asString();

        request(token).body(Map.of("enabled", false)).put("/api/automation/scenarios/enabled").then().statusCode(200);
        assertThat(automation.getFarmsOverview(owner).farms().stream()
                .flatMap(farm -> farm.greenhouses().stream()).flatMap(greenhouse -> greenhouse.scenarios().stream()))
                .allMatch(scenario -> !scenario.enabled());
        assertThat(automation.getFarmsOverview(otherOwner).farms().stream()
                .flatMap(farm -> farm.greenhouses().stream()).flatMap(greenhouse -> greenhouse.scenarios().stream()))
                .allMatch(scenario -> scenario.enabled());
        request(token).body(Map.of("enabled", true)).put("/api/automation/scenarios/enabled").then().statusCode(200);
        var restored = automation.getFarmsOverview(owner).farms().stream()
                .flatMap(farm -> farm.greenhouses().stream()).flatMap(greenhouse -> greenhouse.scenarios().stream()).toList();
        assertThat(restored).allMatch(scenario -> scenario.enabled());
        assertThat(restored.stream().map(scenario -> scenario.config()).toList()).isEqualTo(settings);
        assertThat(request(primary).get("/api/automation/farms").asString()).isEqualTo(realBefore);
        verifyNoInteractions(publisher);
    }

    @Test
    void greenhouseMicroclimatesHaveDistinctHistoriesAndSurviveSimulationTicks() throws Exception {
        Response session = start();
        String token = session.path("access_token");
        UUID id = UUID.fromString(session.path("space.id"));
        var space = spaces.findById(id).orElseThrow();
        assertThat(space.templateVersion).isEqualTo(2);
        List<Map<String, Object>> controllers = request(token).get("/api/demo/status")
                .jsonPath().getList("devices.findAll { it.profile == 'controller' }");
        assertThat(controllers).hasSize(4);
        for (String metric : List.of("temperature", "humidity", "moisture")) {
            assertThat(controllers.stream().map(device -> ((Map<?, ?>) device.get("state")).get(metric)))
                    .doesNotHaveDuplicates();
        }
        List<Integer> nativeIds = jdbc.queryForList("select native_device_id from demo_devices where space_id = ? and native_device_id is not null", Integer.class, id);
        for (String type : List.of("AIR_TEMPERATURE", "AIR_HUMIDITY", "SOIL_MOISTURE")) {
            Set<List<Double>> histories = new HashSet<>();
            for (int nativeId : nativeIds) {
                List<Double> readings = jdbc.queryForList("select r.value_numeric from sensor_readings r join sensors s on s.id = r.sensor_id where s.device_id = ? and s.type = ? order by r.ts, r.id", Double.class, nativeId, type);
                assertThat(readings).hasSizeGreaterThan(20).allMatch(value -> Double.isFinite(value) && value >= 0 && value <= 100);
                assertThat(new HashSet<>(readings)).hasSizeGreaterThan(1);
                histories.add(readings);
            }
            assertThat(histories).hasSize(4);
        }
        for (int nativeId : nativeIds) {
            var readings = new TreeMap<LocalDateTime, Double>();
            jdbc.query("select r.ts, r.value_numeric from sensor_readings r join sensors s on s.id = r.sensor_id where s.device_id = ? and s.type = 'SOIL_MOISTURE' order by r.ts, r.id",
                    (org.springframework.jdbc.core.RowCallbackHandler) row -> readings.put(row.getTimestamp(1).toLocalDateTime(), row.getDouble(2)), nativeId);
            List<LocalDateTime> waterings = jdbc.query("select started_at from pump_watering_sessions where device_id = ? order by started_at",
                    (row, index) -> row.getTimestamp(1).toLocalDateTime(), nativeId);
            assertThat(waterings).hasSize(7);
            int rises = 0;
            Map.Entry<LocalDateTime, Double> previous = null;
            for (var reading : readings.entrySet()) {
                if (previous != null && reading.getValue() > previous.getValue()) {
                    LocalDateTime from = previous.getKey();
                    assertThat(waterings).anyMatch(at -> at.isAfter(from.minusSeconds(36)) && !at.isAfter(reading.getKey()));
                    rises++;
                }
                previous = reading;
            }
            assertThat(rises).isGreaterThanOrEqualTo(6);
        }
        assertThat(jdbc.queryForList("select distinct planned_duration_s from pump_watering_sessions where user_id = ?", Integer.class, space.dataUserId))
                .hasSize(4);
        assertThat(jdbc.query("select max(started_at) from pump_watering_sessions where user_id = ? group by device_id",
                (row, index) -> row.getTimestamp(1).toLocalDateTime().getHour(), space.dataUserId)).doesNotHaveDuplicates();
        var profiles = new HashMap<UUID, Object>();
        for (var controller : controllers) {
            UUID deviceId = UUID.fromString(controller.get("id").toString());
            Map<String, Object> state = mapper.convertValue(controller.get("state"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            profiles.put(deviceId, state.get("environment"));
            state.put("temperature", 24); state.put("humidity", 60);
            jdbc.update("update demo_devices set state_json = ?, updated_at = ? where id = ?", mapper.writeValueAsString(state),
                    LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10), deviceId);
        }
        assertThat(demo.tick(id).telemetry()).isTrue();
        List<Map<String, Object>> after = request(token).get("/api/demo/status").jsonPath().getList("devices.findAll { it.profile == 'controller' }");
        for (String metric : List.of("temperature", "humidity", "moisture")) {
            assertThat(after.stream().map(device -> ((Map<?, ?>) device.get("state")).get(metric))).doesNotHaveDuplicates();
        }
        for (var controller : after) {
            assertThat(((Map<?, ?>) controller.get("state")).get("environment"))
                    .isEqualTo(profiles.get(UUID.fromString(controller.get("id").toString())));
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void previousTemplateKeepsStoredReadingsUntilExplicitReset() throws Exception {
        Response session = start();
        String token = session.path("access_token");
        UUID id = UUID.fromString(session.path("space.id"));
        Map<String, Object> controller = request(token).get("/api/demo/status").path("devices.find { it.profile == 'controller' }");
        UUID deviceId = UUID.fromString(controller.get("id").toString());
        Map<String, Object> state = mapper.convertValue(controller.get("state"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        state.remove("environment"); state.put("temperature", 17); state.put("humidity", 83); state.put("moisture", 92);
        jdbc.update("update demo_devices set state_json = ? where id = ?", mapper.writeValueAsString(state), deviceId);
        jdbc.update("update demo_spaces set template_version = 1 where id = ?", id);
        long readings = jdbc.queryForObject("select count(*) from sensor_readings", Long.class);
        Response resumed = request(null).cookie("gh_demo_refresh", session.cookie("gh_demo_refresh")).post("/api/demo/refresh");
        resumed.then().statusCode(200);
        assertThat(spaces.findById(id).orElseThrow().templateVersion).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sensor_readings", Long.class)).isEqualTo(readings);
        assertThat(jdbc.queryForObject("select state_json from demo_devices where id = ?", String.class, deviceId)).isEqualTo(mapper.writeValueAsString(state));
        jdbc.update("update demo_devices set updated_at = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), deviceId);
        demo.tick(id);
        Map<String, Object> legacy = mapper.readValue(jdbc.queryForObject("select state_json from demo_devices where id = ?", String.class, deviceId), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertThat(((Number) legacy.get("temperature")).doubleValue()).isBetween(17.0, 30.0);
        assertThat(((Number) legacy.get("humidity")).doubleValue()).isEqualTo(83);
        assertThat(legacy).doesNotContainKey("environment");
        Response reset = request(resumed.path("access_token")).cookie("gh_demo_refresh", session.cookie("gh_demo_refresh")).post("/api/demo/reset");
        reset.then().statusCode(200);
        assertThat(spaces.findById(id).orElseThrow().templateVersion).isEqualTo(2);
        request(reset.path("access_token")).get("/api/demo/status").then().statusCode(200)
                .body("devices.findAll { it.profile == 'controller' && it.state.environment != null }.size()", equalTo(4));
        verifyNoInteractions(publisher);
    }

    private Response start() {
        Response response = request(null).body(Map.of("locale", "ru", "timezone", "Europe/Moscow")).post("/api/demo/start");
        response.then().log().ifError().statusCode(200).body("space.saved", equalTo(false));
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly").contains("SameSite=Lax");
        return response;
    }
    private io.restassured.specification.RequestSpecification request(String token) {
        var request = given().baseUri("http://localhost").port(port).header("Origin", "https://growerhub.ru").contentType("application/json");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }
    private UserEntity account() {
        return users.saveAndFlush(UserEntity.create("demo-test-" + UUID.randomUUID() + "@example.com", "User", "user", true, LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void admissionUsesClientAddressOnlyFromConfiguredReverseProxy() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Origin", "https://growerhub.ru");
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Real-IP", "192.0.2.1");
        var first = authFacade.startDemo(null, "ru", "UTC", request,
                new org.springframework.mock.web.MockHttpServletResponse()).space();
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "192.0.2.2");
        var spoofed = authFacade.startDemo(null, "ru", "UTC", request,
                new org.springframework.mock.web.MockHttpServletResponse()).space();
        request.setRemoteAddr("127.0.0.1");
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "198.51.100.10");
        var forwarded = authFacade.startDemo(null, "ru", "UTC", request,
                new org.springframework.mock.web.MockHttpServletResponse()).space();
        String admission = spaces.findById(first.id()).orElseThrow().admissionKey;
        assertThat(spaces.findById(spoofed.id()).orElseThrow().admissionKey).isEqualTo(admission);
        assertThat(spaces.findById(forwarded.id()).orElseThrow().admissionKey).isEqualTo(admission);
        assertThat(admission).doesNotContain("198.51.100.10");
    }
}
