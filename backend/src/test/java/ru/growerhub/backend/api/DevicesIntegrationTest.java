package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
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
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;
import ru.growerhub.backend.device.DeviceShadowStore;
import ru.growerhub.backend.mqtt.MqttMessageHandler;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevicesIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private DeviceStateLastRepository deviceStateLastRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceShadowStore shadowStore;

    @Autowired
    private MqttMessageHandler mqttMessageHandler;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        shadowStore.clear();
    }

    @Test
    void updateDeviceStatusCreatesDeviceAndSensorData() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "dev-1");
        payload.put("soil_moisture", 12.5);
        payload.put("air_temperature", 21.0);
        payload.put("air_humidity", 40.0);
        payload.put("is_watering", false);
        payload.put("is_light_on", true);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/device/dev-1/status")
                .then()
                .statusCode(200)
                .body("message", equalTo("Status updated"));

        DeviceEntity stored = deviceRepository.findByDeviceId("dev-1").orElse(null);
        Assertions.assertNotNull(stored);
        Assertions.assertEquals("Watering Device dev-1", stored.getName());
        Assertions.assertEquals(12.5, stored.getSoilMoisture());
        Assertions.assertEquals(1, sensorDataRepository.count());
    }

    @Test
    void updateDeviceStatusAllowsAnonymous() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "dev-1-public");
        payload.put("soil_moisture", 10.0);
        payload.put("air_temperature", 20.0);
        payload.put("air_humidity", 30.0);
        payload.put("is_watering", false);
        payload.put("is_light_on", false);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/device/dev-1-public/status")
                .then()
                .statusCode(200)
                .body("message", equalTo("Status updated"));

        Assertions.assertEquals(1, sensorDataRepository.count());
    }

    @Test
    void updateDeviceStatusRequiresFields() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "dev-2");
        payload.put("air_temperature", 21.0);
        payload.put("air_humidity", 40.0);
        payload.put("is_watering", false);
        payload.put("is_light_on", true);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/device/dev-2/status")
                .then()
                .statusCode(422);
    }

    @Test
    void getDeviceSettingsCreatesDefaults() {
        given()
                .when()
                .get("/api/device/dev-3/settings")
                .then()
                .statusCode(200)
                .body("target_moisture", equalTo(40.0f))
                .body("watering_duration", equalTo(30))
                .body("watering_timeout", equalTo(300))
                .body("light_on_hour", equalTo(6))
                .body("light_off_hour", equalTo(22))
                .body("light_duration", equalTo(16))
                .body("update_available", equalTo(false));
    }

    @Test
    void updateDeviceSettingsNotFound() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("target_moisture", 55.0);
        payload.put("watering_duration", 10);
        payload.put("watering_timeout", 100);
        payload.put("light_on_hour", 5);
        payload.put("light_off_hour", 20);
        payload.put("light_duration", 15);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .put("/api/device/missing/settings")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Device not found"));
    }

    @Test
    void updateDeviceSettingsUpdatesFields() {
        DeviceEntity device = createDevice("dev-4", null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("target_moisture", 55.0);
        payload.put("watering_duration", 10);
        payload.put("watering_timeout", 100);
        payload.put("watering_speed_lph", 1.5);
        payload.put("light_on_hour", 5);
        payload.put("light_off_hour", 20);
        payload.put("light_duration", 15);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .put("/api/device/" + device.getDeviceId() + "/settings")
                .then()
                .statusCode(200)
                .body("message", equalTo("Settings updated"));

        DeviceEntity stored = deviceRepository.findById(device.getId()).orElse(null);
        Assertions.assertNotNull(stored);
        Assertions.assertEquals(55.0, stored.getTargetMoisture());
        Assertions.assertEquals(1.5, stored.getWateringSpeedLph());
    }

    @Test
    void listDevicesUsesState() throws Exception {
        DeviceEntity device = createDevice("dev-5", null);
        Map<String, Object> manual = new HashMap<>();
        manual.put("status", "running");
        Map<String, Object> state = new HashMap<>();
        state.put("manual_watering", manual);
        state.put("fw_ver", "v2");
        DeviceStateLastEntity stored = DeviceStateLastEntity.create();
        stored.setDeviceId(device.getDeviceId());
        stored.setStateJson(objectMapper.writeValueAsString(state));
        stored.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceStateLastRepository.save(stored);

        given()
                .when()
                .get("/api/devices")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].firmware_version", equalTo("v2"))
                .body("[0].is_watering", equalTo(true))
                .body("[0].is_online", equalTo(true));
    }

    @Test
    void listMyDevicesFiltersAndRequiresAuth() {
        UserEntity user = createUser("owner@example.com", "user");
        UserEntity other = createUser("other@example.com", "user");
        createDevice("dev-6", user);
        createDevice("dev-7", other);
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].device_id", equalTo("dev-6"));

        given()
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void listMyDevicesHandlesStateJson() throws Exception {
        UserEntity user = createUser("state-owner@example.com", "user");
        DeviceEntity device = createDevice("dev-state", user);
        Map<String, Object> manual = new HashMap<>();
        manual.put("status", "running");
        Map<String, Object> state = new HashMap<>();
        state.put("manual_watering", manual);
        state.put("fw_ver", "v3");
        DeviceStateLastEntity stored = DeviceStateLastEntity.create();
        stored.setDeviceId(device.getDeviceId());
        stored.setStateJson(objectMapper.writeValueAsString(state));
        stored.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceStateLastRepository.save(stored);
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].firmware_version", equalTo("v3"))
                .body("[0].is_watering", equalTo(true));
    }

    @Test
    void mqttStatePopulatesAirTemperatureInMyDevices() {
        UserEntity user = createUser("mqtt-owner@example.com", "user");
        DeviceEntity device = createDevice("dev-mqtt-temp", user);
        String token = buildToken(user.getId());
        String payload = """
                {"air_temperature":23.5,"air_humidity":44.0}
                """;

        mqttMessageHandler.handleStateMessage(
                "gh/dev/" + device.getDeviceId() + "/state",
                payload.getBytes(StandardCharsets.UTF_8)
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(200)
                .body("[0].air_temperature", equalTo(23.5f));
    }

    @Test
    void httpStatusPopulatesAirTemperatureInMyDevices() {
        UserEntity user = createUser("http-owner@example.com", "user");
        DeviceEntity device = createDevice("dev-http-temp", user);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getDeviceId());
        payload.put("soil_moisture", 10.0);
        payload.put("air_temperature", 22.0);
        payload.put("air_humidity", 40.0);
        payload.put("is_watering", false);
        payload.put("is_light_on", true);

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/device/" + device.getDeviceId() + "/status")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(200)
                .body("[0].air_temperature", equalTo(22.0f));
    }

    @Test
    void coldStartLoadsAirTemperatureFromDbState() throws Exception {
        UserEntity user = createUser("cold-owner@example.com", "user");
        DeviceEntity device = createDevice("dev-cold", user);
        String token = buildToken(user.getId());

        Map<String, Object> state = new HashMap<>();
        state.put("air_temperature", 19.0);
        state.put("air_humidity", 55.0);
        DeviceStateLastEntity record = DeviceStateLastEntity.create();
        record.setDeviceId(device.getDeviceId());
        record.setStateJson(objectMapper.writeValueAsString(state));
        record.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        deviceStateLastRepository.save(record);
        shadowStore.clear();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/devices/my")
                .then()
                .statusCode(200)
                .body("[0].air_temperature", equalTo(19.0f));
    }

    @Test
    void adminDevicesRequiresAdmin() {
        UserEntity user = createUser("basic@example.com", "user");
        UserEntity admin = createUser("boss@example.com", "admin");
        createDevice("dev-8", user);
        String userToken = buildToken(user.getId());
        String adminToken = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/admin/devices")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/admin/devices")
                .then()
                .statusCode(200)
                .body("[0].owner.id", equalTo(user.getId()))
                .body("[0].owner.email", equalTo(user.getEmail()));
    }

    @Test
    void assignToMeHandlesConflict() {
        UserEntity first = createUser("first@example.com", "user");
        UserEntity second = createUser("second@example.com", "user");
        DeviceEntity device = createDevice("dev-9", null);
        String tokenFirst = buildToken(first.getId());
        String tokenSecond = buildToken(second.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", device.getId());

        given()
                .header("Authorization", "Bearer " + tokenFirst)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/devices/assign-to-me")
                .then()
                .statusCode(200)
                .body("user_id", equalTo(first.getId()));

        given()
                .header("Authorization", "Bearer " + tokenSecond)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/devices/assign-to-me")
                .then()
                .statusCode(400)
                .body("detail", equalTo("ustrojstvo uzhe privyazano k drugomu polzovatelju"));
    }

    @Test
    void unassignDeviceForbiddenForStranger() {
        UserEntity owner = createUser("owner2@example.com", "user");
        UserEntity other = createUser("other2@example.com", "user");
        DeviceEntity device = createDevice("dev-10", owner);
        String token = buildToken(other.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/devices/" + device.getId() + "/unassign")
                .then()
                .statusCode(403)
                .body("detail", equalTo("nedostatochno prav dlya otvyazki etogo ustrojstva"));
    }

    @Test
    void adminAssignAndUnassignDevice() {
        UserEntity admin = createUser("admin@example.com", "admin");
        UserEntity user = createUser("member@example.com", "user");
        DeviceEntity device = createDevice("dev-11", null);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/admin/devices/" + device.getId() + "/assign")
                .then()
                .statusCode(200)
                .body("owner.id", equalTo(user.getId()));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/admin/devices/" + device.getId() + "/unassign")
                .then()
                .statusCode(200)
                .body("owner", equalTo(null));
    }

    @Test
    void adminAssignMissingUserReturns404() {
        UserEntity admin = createUser("admin2@example.com", "admin");
        DeviceEntity device = createDevice("dev-12", null);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", 9999);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/admin/devices/" + device.getId() + "/assign")
                .then()
                .statusCode(404)
                .body("detail", equalTo("polzovatel' ne najden"));
    }

    @Test
    void deleteDeviceRemovesSensorData() {
        DeviceEntity device = createDevice("dev-13", null);
        SensorDataEntity sensor = SensorDataEntity.create();
        sensor.setDeviceId(device.getDeviceId());
        sensor.setSoilMoisture(1.0);
        sensor.setAirTemperature(2.0);
        sensor.setAirHumidity(3.0);
        sensor.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        sensorDataRepository.save(sensor);

        given()
                .when()
                .delete("/api/device/" + device.getDeviceId())
                .then()
                .statusCode(200)
                .body("message", equalTo("Device deleted"));

        Assertions.assertEquals(0, sensorDataRepository.count());
        Assertions.assertTrue(deviceRepository.findById(device.getId()).isEmpty());
    }

    @Test
    void deviceValidationEndpointsReturn422() {
        Map<String, Object> assignPayload = new HashMap<>();
        String token = buildToken(createUser("validate@example.com", "user").getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(assignPayload)
                .when()
                .post("/api/devices/assign-to-me")
                .then()
                .statusCode(422);

        UserEntity admin = createUser("validate-admin@example.com", "admin");
        String adminToken = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(new HashMap<>())
                .when()
                .post("/api/admin/devices/1/assign")
                .then()
                .statusCode(422);

        Map<String, Object> settingsPayload = new HashMap<>();
        settingsPayload.put("watering_duration", 10);
        settingsPayload.put("watering_timeout", 100);
        settingsPayload.put("light_on_hour", 5);
        settingsPayload.put("light_off_hour", 20);
        settingsPayload.put("light_duration", 15);

        given()
                .contentType("application/json")
                .body(settingsPayload)
                .when()
                .put("/api/device/dev-14/settings")
                .then()
                .statusCode(422);
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
        device.setUser(owner);
        device.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        return deviceRepository.save(device);
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
}
