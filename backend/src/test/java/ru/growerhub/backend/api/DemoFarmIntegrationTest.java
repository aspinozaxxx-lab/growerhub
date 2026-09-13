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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    @Autowired ru.growerhub.backend.common.config.DemoSettings demoSettings;
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
