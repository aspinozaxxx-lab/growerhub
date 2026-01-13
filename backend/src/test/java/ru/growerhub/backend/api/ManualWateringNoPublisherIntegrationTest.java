package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

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
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.engine.PumpService;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.internal.UserRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"DEBUG=false", "MQTT_HOST="}
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualWateringNoPublisherIntegrationTest extends IntegrationTestBase {

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
    private PumpService pumpService;

    @Autowired
    private PumpPlantBindingRepository pumpPlantBindingRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void startReturns503WhenPublisherMissing() {
        UserEntity user = createUser("owner-no-pub@example.com", "user");
        DeviceEntity device = createDevice("mw-no-pub", user);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = createPlant(user, "Mint");
        bindPump(pump, plant, 2000);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("duration_s", 10);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/pumps/" + pump.getId() + "/watering/start")
                .then()
                .statusCode(503)
                .body("detail", equalTo("MQTT publisher unavailable"));
    }

    @Test
    void debugEndpointsAreNotRegistered() {
        given()
                .when()
                .get("/_debug/manual-watering/config")
                .then()
                .statusCode(404);

        given()
                .queryParam("device_id", "debug-missing")
                .when()
                .get("/_debug/manual-watering/snapshot")
                .then()
                .statusCode(404);

        Map<String, Object> payload = new HashMap<>();
        payload.put("device_id", "debug-missing");
        payload.put("state", Map.of("manual_watering", Map.of("status", "idle")));

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/_debug/shadow/state")
                .then()
                .statusCode(404);
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
        return deviceRepository.save(device);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setUser(owner);
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
}




