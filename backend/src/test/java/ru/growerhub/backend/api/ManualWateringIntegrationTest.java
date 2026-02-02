package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.mqtt.AckStore;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.engine.PumpService;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ManualWateringIntegrationTest.PublisherConfig.class)
class ManualWateringIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PumpPlantBindingRepository pumpPlantBindingRepository;

    @Autowired
    private PumpService pumpService;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantJournalEntryRepository plantJournalEntryRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;

    @Autowired
    private PlantMetricSampleRepository plantMetricSampleRepository;

    @Autowired
    private DeviceStateLastRepository deviceStateLastRepository;

    @Autowired
    private AckStore ackStore;

    @Autowired
    private DeviceShadowStore shadowStore;

    @Autowired
    private TestPublisher testPublisher;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        ackStore.clear();
        shadowStore.clear();
        testPublisher.reset();
    }

    @Test
    void startReturnsCorrelationAndStoresShadow() {
        UserEntity user = createUser("owner@example.com", "user");
        DeviceEntity device = createDevice("mw-start-1", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Basil");
        bindPump(pump, plant, 2000);

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 20);

        String token = buildToken(user.getId());

        String correlationId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue())
                .extract()
                .path("correlation_id");

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        Assertions.assertEquals(device.getDeviceId(), published.deviceId());
        Assertions.assertTrue(published.cmd() instanceof CmdPumpStart);
        CmdPumpStart cmd = (CmdPumpStart) published.cmd();
        Assertions.assertEquals("pump.start", cmd.type());
        Assertions.assertEquals(20, cmd.durationS());
        Assertions.assertEquals(correlationId, cmd.correlationId());

        Assertions.assertEquals(0, plantJournalEntryRepository.count());
        Assertions.assertEquals(0, plantJournalWateringDetailsRepository.count());
        Assertions.assertEquals(0, plantMetricSampleRepository.count());

        Map<String, Object> view = shadowStore.getManualWateringView(device.getDeviceId());
        Assertions.assertNotNull(view);
        Assertions.assertEquals("running", view.get("status"));

        DeviceStateLastEntity state = deviceStateLastRepository.findByDeviceId(device.getDeviceId()).orElse(null);
        Assertions.assertNotNull(state);
        Assertions.assertTrue(state.getStateJson().contains("manual_watering"));
    }

    @Test
    void startCalculatesDurationFromVolume() {
        UserEntity user = createUser("owner-volume@example.com", "user");
        DeviceEntity device = createDevice("mw-start-2", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Mint");
        bindPump(pump, plant, 2000);

        Map<String, Object> payload = new HashMap<>();
        payload.put("water_volume_l", 1.0);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue());

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        CmdPumpStart cmd = (CmdPumpStart) published.cmd();
        Assertions.assertEquals(1800, cmd.durationS());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/pumps/" + pump.getId() + "/watering/status")
                .then()
                .statusCode(200)
                .body("duration_s", equalTo(1800));

        Assertions.assertEquals(0, plantJournalEntryRepository.count());
        Assertions.assertEquals(0, plantJournalWateringDetailsRepository.count());
    }

    @Test
    void updatePumpBindingsReturnsOk() {
        UserEntity user = createUser("owner-bindings@example.com", "user");
        DeviceEntity device = createDevice("mw-bindings", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Basil");
        String token = buildToken(user.getId());

        Map<String, Object> payload = Map.of(
                "items",
                List.of(Map.of("plant_id", plant.getId(), "rate_ml_per_hour", 1800))
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .put("/api/pumps/" + pump.getId() + "/bindings")
                .then()
                .statusCode(200)
                .body("ok", equalTo(true));

        Assertions.assertEquals(1, pumpPlantBindingRepository.findAllByPump_Id(pump.getId()).size());
    }

    @Test
    void startRequiresAuth() {
        DeviceEntity device = createDevice("mw-auth-1", null);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 10);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void startRejectsNoPlants() {
        UserEntity user = createUser("owner-noplants@example.com", "user");
        DeviceEntity device = createDevice("mw-noplants", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("water_volume_l", 0.3);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(400)
                .body("detail", equalTo("nasos ne privyazan ni k odnomu rasteniyu"));

        Assertions.assertEquals(0, testPublisher.getPublished().size());
        Assertions.assertEquals(0, plantJournalEntryRepository.count());
    }

    @Test
    void startRejectsMissingDurationAndVolume() {
        UserEntity user = createUser("owner-noval@example.com", "user");
        DeviceEntity device = createDevice("mw-noval", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Rose");
        bindPump(pump, plant, 2000);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(400)
                .body("detail", equalTo("ukazhite water_volume_l ili duration_s dlya starta poliva"));
    }

    @Test
    void startRejectsNotOwner() {
        UserEntity owner = createUser("owner-guard@example.com", "user");
        UserEntity other = createUser("guest@example.com", "user");
        DeviceEntity device = createDevice("mw-guard", owner);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(owner, "Mint");
        bindPump(pump, plant, 2000);
        String token = buildToken(other.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(403)
                .body("detail", equalTo("nedostatochno prav dlya etogo nasosa"));
    }

    @Test
    void startReturns404WhenPumpMissing() {
        UserEntity user = createUser("owner-missing@example.com", "user");
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/99999/watering/start")
                .then()
                .statusCode(404)
                .body("detail", equalTo("nasos ne naiden"));
    }

    @Test
    void stopCreatesJournalWithActualDurationAndIdempotent() throws InterruptedException {
        UserEntity user = createUser("owner-stop@example.com", "user");
        DeviceEntity device = createDevice("mw-stop", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Basil");
        bindPump(pump, plant, 2000);

        String token = buildToken(user.getId());

        Map<String, Object> startPayload = new HashMap<>();
        startPayload.put("duration_s", 10);
        startPayload.put("water_volume_l", 1.0);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(startPayload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue());

        Thread.sleep(2500);

        String stopCorrelation = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/stop")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue())
                .extract()
                .path("correlation_id");

        Assertions.assertEquals(2, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(1);
        Assertions.assertEquals(device.getDeviceId(), published.deviceId());
        Assertions.assertTrue(published.cmd() instanceof CmdPumpStop);
        CmdPumpStop cmd = (CmdPumpStop) published.cmd();
        Assertions.assertEquals("pump.stop", cmd.type());
        Assertions.assertEquals(stopCorrelation, cmd.correlationId());

        Assertions.assertEquals(1, plantJournalEntryRepository.count());
        Assertions.assertEquals(1, plantJournalWateringDetailsRepository.count());
        PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository.findAll().get(0);
        Assertions.assertTrue(details.getDurationS() < 10);
        Assertions.assertTrue(details.getDurationS() >= 0);
        Assertions.assertTrue(details.getWaterVolumeL() > 0.0);
        Assertions.assertTrue(details.getWaterVolumeL() < 1.0);
        double expectedVolume = 1.0 * details.getDurationS() / 10.0;
        Assertions.assertTrue(Math.abs(details.getWaterVolumeL() - expectedVolume) < 0.05);
        Assertions.assertEquals(1, plantMetricSampleRepository.count());

        Map<String, Object> view = shadowStore.getManualWateringView(device.getDeviceId());
        Assertions.assertEquals("completed", view.get("status"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/stop")
                .then()
                .statusCode(200);

        Assertions.assertEquals(1, plantJournalEntryRepository.count());
        Assertions.assertEquals(1, plantJournalWateringDetailsRepository.count());
    }

    @Test
    void rebootPublishesCommand() {
        UserEntity user = createUser("owner-reboot@example.com", "user");
        DeviceEntity device = createDevice("mw-reboot", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/reboot")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue())
                .body("message", equalTo("reboot command published"));

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        Assertions.assertTrue(published.cmd() instanceof CmdReboot);
        CmdReboot cmd = (CmdReboot) published.cmd();
        Assertions.assertEquals("reboot", cmd.type());
    }

    @Test
    void statusUsesShadowStore() {
        UserEntity user = createUser("owner-status@example.com", "user");
        DeviceEntity device = createDevice("mw-status", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());

        DeviceShadowState.ManualWateringState manual = new DeviceShadowState.ManualWateringState(
                "running",
                30,
                LocalDateTime.now(ZoneOffset.UTC).withNano(0),
                30,
                "corr-status",
                null,
                null,
                null,
                null
        );
        shadowStore.updateFromState(
                device.getDeviceId(),
                new DeviceShadowState(manual, null, null, null, null, null, null, null, null, null)
        );

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/pumps/" + pump.getId() + "/watering/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("running"))
                .body("duration_s", equalTo(30))
                .body("duration", equalTo(30))
                .body("source", equalTo("calculated"));
    }

    @Test
    void statusReturnsIdleWhenNoState() {
        UserEntity user = createUser("owner-idle@example.com", "user");
        DeviceEntity device = createDevice("mw-idle", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/pumps/" + pump.getId() + "/watering/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("idle"))
                .body("source", equalTo("no_state"))
                .body("offline_reason", equalTo("device_offline"));
    }

    @Test
    void ackReturnsExisting() {
        UserEntity user = createUser("owner-ack@example.com", "user");
        String token = buildToken(user.getId());

        ManualWateringAck ack = new ManualWateringAck("ack-1", "accepted", null, "ok");
        ackStore.put("dev-ack", ack);

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("correlation_id", "ack-1")
                .when()
                .get("/api/pumps/watering/ack")
                .then()
                .statusCode(200)
                .body("correlation_id", equalTo("ack-1"))
                .body("result", equalTo("accepted"))
                .body("status", equalTo("ok"));
    }

    @Test
    void ackReturns404WhenMissing() {
        UserEntity user = createUser("owner-ack-missing@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("correlation_id", "missing")
                .when()
                .get("/api/pumps/watering/ack")
                .then()
                .statusCode(404)
                .body("detail", equalTo("ACK eshche ne poluchen ili udalen po TTL"));
    }

    @Test
    void waitAckReturnsExisting() {
        UserEntity user = createUser("owner-wait@example.com", "user");
        String token = buildToken(user.getId());

        ManualWateringAck ack = new ManualWateringAck("ack-wait", "accepted", "ok", "done");
        ackStore.put("dev-wait", ack);

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("correlation_id", "ack-wait")
                .queryParam("timeout_s", 2)
                .when()
                .get("/api/pumps/watering/wait-ack")
                .then()
                .statusCode(200)
                .body("correlation_id", equalTo("ack-wait"))
                .body("result", equalTo("accepted"))
                .body("reason", equalTo("ok"))
                .body("status", equalTo("done"));
    }

    @Test
    void waitAckReturnsTimeout() {
        UserEntity user = createUser("owner-wait-timeout@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("correlation_id", "missing")
                .queryParam("timeout_s", 1)
                .when()
                .get("/api/pumps/watering/wait-ack")
                .then()
                .statusCode(408)
                .body("detail", equalTo("ACK ne poluchen v zadannoe vremya"));
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, role, true, now, now);
        return userRepository.save(user);
    }

    private DeviceEntity createDevice(String deviceId, UserEntity owner) {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId(deviceId);
        device.setName("Device " + deviceId);
        device.setUserId(owner != null ? owner.getId() : null);
        return deviceRepository.save(device);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setUserId(owner != null ? owner.getId() : null);
        plant.setName(name);
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantRepository.save(plant);
    }

    private void bindPump(PumpEntity pump, PlantEntity plant, int rate) {
        PumpPlantBindingEntity link = PumpPlantBindingEntity.create();
        link.setPump(pump);
        link.setPlantId(plant.getId());
        link.setRateMlPerHour(rate);
        pumpPlantBindingRepository.save(link);
    }

    private String buildToken(int userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(Date.from(java.time.Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    @TestConfiguration
    static class PublisherConfig {

        @Bean
        TestPublisher testPublisher() {
            return new TestPublisher();
        }
    }

    static class TestPublisher implements MqttPublisher {
        private final List<PublishedCommand> published = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failNext = new AtomicBoolean(false);

        @Override
        public void publishCmd(String deviceId, Object cmd) {
            if (failNext.compareAndSet(true, false)) {
                throw new RuntimeException("publisher failure");
            }
            published.add(new PublishedCommand(deviceId, cmd));
        }

        void failNext() {
            failNext.set(true);
        }

        void reset() {
            published.clear();
            failNext.set(false);
        }

        List<PublishedCommand> getPublished() {
            return published;
        }
    }

    private record PublishedCommand(String deviceId, Object cmd) {
    }
}









