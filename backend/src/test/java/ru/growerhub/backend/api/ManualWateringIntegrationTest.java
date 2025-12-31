package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantEntity;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.db.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantRepository;
import ru.growerhub.backend.device.DeviceShadowStore;
import ru.growerhub.backend.mqtt.AckStore;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.mqtt.model.ManualWateringState;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

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
    private PlantRepository plantRepository;

    @Autowired
    private PlantDeviceRepository plantDeviceRepository;

    @Autowired
    private PlantJournalEntryRepository plantJournalEntryRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;

    @Autowired
    private DeviceStateLastRepository deviceStateLastRepository;

    @Autowired
    private AckStore ackStore;

    @Autowired
    private DeviceShadowStore shadowStore;

    @Autowired
    private TestPublisher testPublisher;

    @Autowired
    private ObjectMapper objectMapper;

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
    void startReturnsCorrelationAndCreatesJournal() {
        UserEntity user = createUser("owner@example.com", "user");
        DeviceEntity device = createDevice("mw-start-1", user, null);
        PlantEntity plant = createPlant(user, "Basil");
        linkPlant(plant, device);

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("duration_s", 20);

        String token = buildToken(user.getId());

        String correlationId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
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

        Assertions.assertEquals(1, plantJournalEntryRepository.count());
        Assertions.assertEquals(1, plantJournalWateringDetailsRepository.count());
        PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository.findAll().get(0);
        Assertions.assertEquals(20, details.getDurationS());
        Assertions.assertEquals(0.0, details.getWaterVolumeL());

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
        DeviceEntity device = createDevice("mw-start-2", user, 2.0);
        PlantEntity plant = createPlant(user, "Mint");
        linkPlant(plant, device);

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("water_volume_l", 1.0);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue());

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        CmdPumpStart cmd = (CmdPumpStart) published.cmd();
        Assertions.assertEquals(1800, cmd.durationS());

        PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository.findAll().get(0);
        Assertions.assertEquals(1800, details.getDurationS());
        Assertions.assertEquals(1.0, details.getWaterVolumeL());
    }

    @Test
    void startRequiresAuth() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "mw-auth-1");
        payload.put("duration_s", 10);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void startRejectsNoPlants() {
        UserEntity user = createUser("owner-noplants@example.com", "user");
        DeviceEntity device = createDevice("mw-noplants", user, 1.5);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("water_volume_l", 0.3);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Устройство не привязано ни k odnomu rasteniyu"));

        Assertions.assertEquals(0, testPublisher.getPublished().size());
        Assertions.assertEquals(0, plantJournalEntryRepository.count());
    }

    @Test
    void startRejectsMissingDurationAndVolume() {
        UserEntity user = createUser("owner-noval@example.com", "user");
        DeviceEntity device = createDevice("mw-noval", user, 1.0);
        PlantEntity plant = createPlant(user, "Rose");
        linkPlant(plant, device);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(400)
                .body("detail", equalTo("ukazhite water_volume_l ili duration_s dlya starta poliva"));
    }

    @Test
    void startRejectsWhenRunning() {
        UserEntity user = createUser("owner-running@example.com", "user");
        DeviceEntity device = createDevice("mw-running", user, 1.0);
        PlantEntity plant = createPlant(user, "Mint");
        linkPlant(plant, device);

        ManualWateringState manual = new ManualWateringState(
                "running",
                60,
                LocalDateTime.now(ZoneOffset.UTC),
                60,
                "shadow-corr"
        );
        shadowStore.updateFromState(device.getDeviceId(), new DeviceState(manual, null, null, null, null, null, null, null, null, null, null, null, null));

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("duration_s", 10);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Полив уже выполняется — повторный запуск запрещён."));

        Assertions.assertEquals(0, testPublisher.getPublished().size());
    }

    @Test
    void startRejectsNotOwner() {
        UserEntity owner = createUser("owner-guard@example.com", "user");
        UserEntity other = createUser("guest@example.com", "user");
        DeviceEntity device = createDevice("mw-guard", owner, 1.0);
        PlantEntity plant = createPlant(owner, "Mint");
        linkPlant(plant, device);
        String token = buildToken(other.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(403)
                .body("detail", equalTo("nedostatochno prav dlya etogo ustrojstva"));
    }

    @Test
    void startReturns404WhenDeviceMissing() {
        UserEntity user = createUser("owner-missing@example.com", "user");
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "missing-device");
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(404)
                .body("detail", equalTo("ustrojstvo ne najdeno"));
    }

    @Test
    void startValidationMissingDeviceId() {
        UserEntity user = createUser("owner-validation@example.com", "user");
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/start")
                .then()
                .statusCode(422);
    }

    @Test
    void stopReturnsCorrelation() {
        UserEntity user = createUser("owner-stop@example.com", "user");
        DeviceEntity device = createDevice("mw-stop", user, 1.0);

        ManualWateringState manual = new ManualWateringState(
                "running",
                45,
                LocalDateTime.now(ZoneOffset.UTC),
                45,
                "shadow-stop"
        );
        shadowStore.updateFromState(device.getDeviceId(), new DeviceState(manual, null, null, null, null, null, null, null, null, null, null, null, null));

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());

        String token = buildToken(user.getId());

        String correlationId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/stop")
                .then()
                .statusCode(200)
                .body("correlation_id", notNullValue())
                .extract()
                .path("correlation_id");

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        Assertions.assertEquals(device.getDeviceId(), published.deviceId());
        Assertions.assertTrue(published.cmd() instanceof CmdPumpStop);
        CmdPumpStop cmd = (CmdPumpStop) published.cmd();
        Assertions.assertEquals("pump.stop", cmd.type());
        Assertions.assertEquals(correlationId, cmd.correlationId());
    }

    @Test
    void stopRejectsWhenIdle() {
        UserEntity user = createUser("owner-stop-idle@example.com", "user");
        DeviceEntity device = createDevice("mw-stop-idle", user, 1.0);

        ManualWateringState manual = new ManualWateringState(
                "idle",
                null,
                null,
                null,
                null
        );
        shadowStore.updateFromState(device.getDeviceId(), new DeviceState(manual, null, null, null, null, null, null, null, null, null, null, null, null));

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/stop")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Полив не выполняется — останавливать нечего."));

        Assertions.assertEquals(0, testPublisher.getPublished().size());
    }

    @Test
    void stopValidationMissingDeviceId() {
        UserEntity user = createUser("owner-stop-validation@example.com", "user");
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/stop")
                .then()
                .statusCode(422);
    }

    @Test
    void rebootPublishesCommand() {
        UserEntity user = createUser("owner-reboot@example.com", "user");
        DeviceEntity device = createDevice("mw-reboot", user, 1.0);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/reboot")
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
    void rebootReturns502WhenPublishFails() {
        UserEntity user = createUser("owner-reboot-fail@example.com", "user");
        DeviceEntity device = createDevice("mw-reboot-fail", user, 1.0);
        String token = buildToken(user.getId());
        testPublisher.failNext();

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/reboot")
                .then()
                .statusCode(502)
                .body("detail", equalTo("Failed to publish manual reboot command"));
    }

    @Test
    void rebootValidationEmptyDeviceId() {
        UserEntity user = createUser("owner-reboot-validation@example.com", "user");
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/manual-watering/reboot")
                .then()
                .statusCode(422);
    }

    @Test
    void statusUsesShadowStore() {
        UserEntity user = createUser("owner-status@example.com", "user");
        DeviceEntity device = createDevice("mw-status", user, 1.0);

        ManualWateringState manual = new ManualWateringState(
                "running",
                30,
                LocalDateTime.now(ZoneOffset.UTC).withNano(0),
                30,
                "corr-status"
        );
        shadowStore.updateFromState(device.getDeviceId(), new DeviceState(manual, null, null, null, null, null, null, null, null, null, null, null, null));

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("device_id", device.getDeviceId())
                .when()
                .get("/api/manual-watering/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("running"))
                .body("duration_s", equalTo(30))
                .body("duration", equalTo(30))
                .body("source", equalTo("calculated"));
    }

    @Test
    void statusUsesDbState() throws Exception {
        UserEntity user = createUser("owner-dbstate@example.com", "user");
        DeviceEntity device = createDevice("mw-dbstate", user, 1.0);

        Map<String, Object> manual = new HashMap<>();
        manual.put("status", "running");
        manual.put("duration_s", 25);
        manual.put("started_at", "2024-01-01T00:00:00Z");
        manual.put("correlation_id", "corr-db");
        manual.put("remaining_s", 10);
        Map<String, Object> state = new HashMap<>();
        state.put("manual_watering", manual);

        DeviceStateLastEntity record = DeviceStateLastEntity.create();
        record.setDeviceId(device.getDeviceId());
        record.setStateJson(objectMapper.writeValueAsString(state));
        record.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceStateLastRepository.save(record);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("device_id", device.getDeviceId())
                .when()
                .get("/api/manual-watering/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("running"))
                .body("duration_s", equalTo(25))
                .body("duration", equalTo(25))
                .body("source", equalTo("db_state"));
    }

    @Test
    void statusFallsBackToDevice() {
        UserEntity user = createUser("owner-fallback@example.com", "user");
        DeviceEntity device = createDevice("mw-fallback", user, 1.0);
        device.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        deviceRepository.save(device);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("device_id", device.getDeviceId())
                .when()
                .get("/api/manual-watering/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("idle"))
                .body("source", equalTo("db_fallback"))
                .body("is_online", equalTo(true));
    }

    @Test
    void statusValidationMissingDeviceId() {
        UserEntity user = createUser("owner-status-validation@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/manual-watering/status")
                .then()
                .statusCode(422);
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
                .get("/api/manual-watering/ack")
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
                .get("/api/manual-watering/ack")
                .then()
                .statusCode(404)
                .body("detail", equalTo("ACK ещё не получен или удалён по TTL"));
    }

    @Test
    void ackValidationMissingCorrelationId() {
        UserEntity user = createUser("owner-ack-validation@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/manual-watering/ack")
                .then()
                .statusCode(422);
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
                .get("/api/manual-watering/wait-ack")
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
                .get("/api/manual-watering/wait-ack")
                .then()
                .statusCode(408)
                .body("detail", equalTo("ACK не получен в заданное время"));
    }

    @Test
    void waitAckValidationTimeout() {
        UserEntity user = createUser("owner-wait-validation@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("correlation_id", "ack-1")
                .queryParam("timeout_s", 0)
                .when()
                .get("/api/manual-watering/wait-ack")
                .then()
                .statusCode(422);
    }

    @Test
    void debugEndpointsReturnOk() {
        Map<String, Object> manual = new HashMap<>();
        manual.put("status", "running");
        Map<String, Object> state = new HashMap<>();
        state.put("manual_watering", manual);

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "debug-1");
        payload.put("state", state);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/_debug/shadow/state")
                .then()
                .statusCode(200)
                .body("ok", equalTo(true));

        given()
                .queryParam("device_id", "debug-1")
                .when()
                .get("/_debug/manual-watering/snapshot")
                .then()
                .statusCode(200)
                .body("view.status", equalTo("running"));

        given()
                .when()
                .get("/_debug/manual-watering/config")
                .then()
                .statusCode(200)
                .body("debug", equalTo(true));
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, role, true, now, now);
        return userRepository.save(user);
    }

    private DeviceEntity createDevice(String deviceId, UserEntity owner, Double wateringSpeedLph) {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId(deviceId);
        device.setName("Device " + deviceId);
        device.setUser(owner);
        device.setWateringSpeedLph(wateringSpeedLph);
        return deviceRepository.save(device);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setUser(owner);
        plant.setName(name);
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantRepository.save(plant);
    }

    private void linkPlant(PlantEntity plant, DeviceEntity device) {
        PlantDeviceEntity link = PlantDeviceEntity.create();
        link.setPlant(plant);
        link.setDevice(device);
        plantDeviceRepository.save(link);
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
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_devices");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM plant_groups");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM sensor_data");
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
