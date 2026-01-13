package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.sensor.SensorType;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.internal.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantMetricSampleRepository plantMetricSampleRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void sensorHistoryFiltersByHours() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = createUser("sensor-owner@example.com", "admin");
        DeviceEntity device = createDevice("hist-device-1", user);

        SensorEntity sensor = createSensor(device, SensorType.SOIL_MOISTURE, 0);
        SensorReadingEntity fresh = SensorReadingEntity.create();
        fresh.setSensor(sensor);
        fresh.setTs(now.minusHours(2));
        fresh.setValueNumeric(31.5);
        sensorReadingRepository.save(fresh);

        SensorReadingEntity stale = SensorReadingEntity.create();
        stale.setSensor(sensor);
        stale.setTs(now.minusDays(2));
        stale.setValueNumeric(28.0);
        sensorReadingRepository.save(stale);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("hours", 24)
                .when()
                .get("/api/sensors/" + sensor.getId() + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].value", equalTo(31.5f));
    }

    @Test
    void plantHistoryFiltersByHoursAndMetric() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = createUser("plant-owner@example.com", "user");
        PlantEntity plant = createPlant(user, "History Plant");

        PlantMetricSampleEntity recent = PlantMetricSampleEntity.create();
        recent.setPlant(plant);
        recent.setMetricType(PlantMetricType.SOIL_MOISTURE);
        recent.setTs(now.minusHours(1));
        recent.setValueNumeric(40.0);
        plantMetricSampleRepository.save(recent);

        PlantMetricSampleEntity old = PlantMetricSampleEntity.create();
        old.setPlant(plant);
        old.setMetricType(PlantMetricType.SOIL_MOISTURE);
        old.setTs(now.minusDays(2));
        old.setValueNumeric(20.0);
        plantMetricSampleRepository.save(old);

        PlantMetricSampleEntity other = PlantMetricSampleEntity.create();
        other.setPlant(plant);
        other.setMetricType(PlantMetricType.AIR_TEMPERATURE);
        other.setTs(now.minusHours(2));
        other.setValueNumeric(21.0);
        plantMetricSampleRepository.save(other);

        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("hours", 24)
                .queryParam("metrics", "SOIL_MOISTURE")
                .when()
                .get("/api/plants/" + plant.getId() + "/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].metric_type", equalTo("SOIL_MOISTURE"))
                .body("[0].value", equalTo(40.0f));
    }

    @Test
    void sensorHistoryDownsamplesTo200Points() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = createUser("sensor-down@example.com", "admin");
        DeviceEntity device = createDevice("many-points", user);
        SensorEntity sensor = createSensor(device, SensorType.AIR_TEMPERATURE, 0);

        List<SensorReadingEntity> bulk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            SensorReadingEntity item = SensorReadingEntity.create();
            item.setSensor(sensor);
            item.setTs(now.minusMinutes(i));
            item.setValueNumeric(20.0 + (i % 3));
            bulk.add(item);
        }
        sensorReadingRepository.saveAll(bulk);

        String token = buildToken(user.getId());
        Response response = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("hours", 1000)
                .when()
                .get("/api/sensors/" + sensor.getId() + "/history")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<String> timestamps = response.jsonPath().getList("ts");
        Assertions.assertTrue(timestamps.size() <= 200);
        List<LocalDateTime> parsed = new ArrayList<>();
        for (String ts : timestamps) {
            parsed.add(LocalDateTime.parse(ts));
        }
        List<LocalDateTime> sorted = new ArrayList<>(parsed);
        sorted.sort(LocalDateTime::compareTo);
        Assertions.assertEquals(sorted, parsed);
    }

    @Test
    void plantHistoryDownsamplesTo200Points() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = createUser("plant-down@example.com", "user");
        PlantEntity plant = createPlant(user, "Downsample");

        List<PlantMetricSampleEntity> bulk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            PlantMetricSampleEntity item = PlantMetricSampleEntity.create();
            item.setPlant(plant);
            item.setMetricType(PlantMetricType.SOIL_MOISTURE);
            item.setTs(now.minusMinutes(i));
            item.setValueNumeric(30.0 + (i % 5));
            bulk.add(item);
        }
        plantMetricSampleRepository.saveAll(bulk);

        String token = buildToken(user.getId());
        Response response = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("hours", 1000)
                .queryParam("metrics", "SOIL_MOISTURE")
                .when()
                .get("/api/plants/" + plant.getId() + "/history")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<String> timestamps = response.jsonPath().getList("ts");
        Assertions.assertTrue(timestamps.size() <= 200);
        List<LocalDateTime> parsed = new ArrayList<>();
        for (String ts : timestamps) {
            parsed.add(LocalDateTime.parse(ts));
        }
        List<LocalDateTime> sorted = new ArrayList<>(parsed);
        sorted.sort(LocalDateTime::compareTo);
        Assertions.assertEquals(sorted, parsed);
    }

    @Test
    void plantHistoryRejectsUnknownMetric() {
        UserEntity user = createUser("plant-bad@example.com", "user");
        PlantEntity plant = createPlant(user, "BadMetric");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("hours", 24)
                .queryParam("metrics", "UNKNOWN")
                .when()
                .get("/api/plants/" + plant.getId() + "/history")
                .then()
                .statusCode(422)
                .body("detail", equalTo("neizvestnyi metric_type: UNKNOWN"));
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

    private SensorEntity createSensor(DeviceEntity device, SensorType type, int channel) {
        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(device.getId());
        sensor.setType(type);
        sensor.setChannel(channel);
        sensor.setDetected(true);
        sensor.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        sensor.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return sensorRepository.save(sensor);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setName(name);
        plant.setUser(owner);
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantRepository.save(plant);
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
        jdbcTemplate.update("DELETE FROM sensor_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM plant_groups");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}







