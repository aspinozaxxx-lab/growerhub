package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.sensor.contract.SensorStatus;
import ru.growerhub.backend.sensor.contract.SensorType;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST=", "SELF_SERVICE_ENABLED=true"}
)
class FarmAutomationIntegrationTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void regularAdminEndpointReturnsOnlyOwnFarmAndFarmIsUnique() {
        UserEntity admin = createUser("farm-admin@example.com", "admin");
        UserEntity other = createUser("farm-other@example.com", "user");
        String adminToken = buildToken(admin.getId());
        String otherToken = buildToken(other.getId());

        createFarm(adminToken, "Ферма администратора");
        createFarm(otherToken, "Чужая ферма");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/automation/farm")
                .then()
                .statusCode(200)
                .body("farm.name", equalTo("Ферма администратора"))
                .body("farm.zones", hasSize(0));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("{\"name\":\"Вторая ферма\"}")
                .when()
                .post("/api/automation/farm")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Ферма уже создана"));
    }

    @Test
    void zoneCreatesInternalBoxAndPlantMovesAtomically() {
        UserEntity owner = createUser("farm-plants@example.com", "user");
        String token = buildToken(owner.getId());
        createFarm(token, "Моя ферма");
        Integer firstZoneId = createZone(token, "Теплица 1");
        Integer secondZoneId = createZone(token, "Теплица 2");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "name":"Томат",
                          "zone_id":%d,
                          "plant_type":"tomato"
                        }
                        """.formatted(firstZoneId))
                .when()
                .post("/api/plants")
                .then()
                .statusCode(200)
                .body("zone.id", equalTo(firstZoneId))
                .body("zone.name", equalTo("Теплица 1"));

        Integer plantId = jdbcTemplate.queryForObject("SELECT id FROM plants WHERE name='Томат'", Integer.class);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":" + secondZoneId + "}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone.id", equalTo(secondZoneId))
                .body("zone.name", equalTo("Теплица 2"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":null}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone", nullValue());

        Integer boxes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM automation_boxes", Integer.class);
        Integer placements = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM automation_box_plants", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(2, boxes);
        org.junit.jupiter.api.Assertions.assertEquals(0, placements);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "name":"Огурец",
                          "zone_id":999999,
                          "plant_type":"cucumber"
                        }
                        """)
                .when()
                .post("/api/plants")
                .then()
                .statusCode(404);

        Integer rolledBackPlants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plants WHERE name='Огурец'",
                Integer.class
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, rolledBackPlants);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":" + firstZoneId + "}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone.id", equalTo(firstZoneId));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/automation/farm/zones/" + firstZoneId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone", nullValue());
    }

    @Test
    void physicalSensorNeedsExplicitReassignmentBetweenZones() {
        UserEntity owner = createUser("farm-slots@example.com", "user");
        String token = buildToken(owner.getId());
        createFarm(token, "Моя ферма");
        Integer firstZoneId = createZone(token, "Теплица 1");
        Integer secondZoneId = createZone(token, "Теплица 2");
        SensorEntity sensor = createSensor(owner);

        String slot = """
                {
                  "role":"AIR_TEMPERATURE_SENSOR",
                  "source_type":"NATIVE_SENSOR",
                  "native_sensor_id":%d
                }
                """.formatted(sensor.getId());
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "]}")
                .when()
                .put("/api/automation/farm/zones/" + firstZoneId + "/slots")
                .then()
                .statusCode(200)
                .body("farm.zones.find { it.id == " + firstZoneId + " }.slots", hasSize(1));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "]}")
                .when()
                .put("/api/automation/farm/zones/" + secondZoneId + "/slots")
                .then()
                .statusCode(409)
                .body("detail", equalTo(
                        "Ресурс уже назначен слоту AIR_TEMPERATURE_SENSOR зоны «Теплица 1»"
                ));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "],\"reassign\":true}")
                .when()
                .put("/api/automation/farm/zones/" + secondZoneId + "/slots")
                .then()
                .statusCode(200)
                .body("farm.zones.find { it.id == " + firstZoneId + " }.slots", hasSize(0))
                .body("farm.zones.find { it.id == " + secondZoneId + " }.slots[0].role",
                        equalTo("AIR_TEMPERATURE_SENSOR"));
    }

    @Test
    void enabledScenarioRequiresReadySlots() {
        UserEntity owner = createUser("farm-readiness@example.com", "user");
        String token = buildToken(owner.getId());
        createFarm(token, "Моя ферма");
        Integer zoneId = createZone(token, "Теплица");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "scenarios":[{
                            "scenario_type":"LIGHT_SCHEDULE",
                            "enabled":true,
                            "config":{}
                          }]
                        }
                        """)
                .when()
                .put("/api/automation/farm/zones/" + zoneId + "/scenarios")
                .then()
                .statusCode(409)
                .body("detail", equalTo(
                        "Нужен Zigbee-ресурс LIGHT_SWITCH с управляемым свойством state"
                ));

        Integer scenarios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_scenario_configs",
                Integer.class
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, scenarios);
    }

    private void createFarm(String token, String name) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post("/api/automation/farm")
                .then()
                .statusCode(200);
    }

    private Integer createZone(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\",\"enabled\":true}")
                .when()
                .post("/api/automation/farm/zones")
                .then()
                .statusCode(200)
                .extract()
                .path("farm.zones.find { it.name == '" + name + "' }.id");
    }

    private SensorEntity createSensor(UserEntity owner) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("farm-native-" + owner.getId());
        device.setName("Farm native device");
        device.setUserId(owner.getId());
        device.setLastSeen(now);
        device = deviceRepository.save(device);

        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(device.getId());
        sensor.setType(SensorType.AIR_TEMPERATURE);
        sensor.setChannel(0);
        sensor.setLabel("Температура");
        sensor.setDetected(true);
        sensor.setStatus(SensorStatus.OK);
        sensor.setCreatedAt(now);
        sensor.setUpdatedAt(now);
        return sensorRepository.save(sensor);
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return userRepository.save(UserEntity.create(email, null, role, true, now, now));
    }

    private String buildToken(int userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM automation_action_log");
        jdbcTemplate.update("DELETE FROM automation_scenario_states");
        jdbcTemplate.update("DELETE FROM automation_scenario_configs");
        jdbcTemplate.update("DELETE FROM automation_resource_bindings");
        jdbcTemplate.update("DELETE FROM automation_box_plants");
        jdbcTemplate.update("DELETE FROM automation_boxes");
        jdbcTemplate.update("DELETE FROM automation_rooms");
        jdbcTemplate.update("DELETE FROM automation_farms");
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM sensor_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM device_service_events");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM zigbee_device_property_readings");
        jdbcTemplate.update("DELETE FROM zigbee_device_state_events");
        jdbcTemplate.update("DELETE FROM zigbee_command_response_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_bridge_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_coordinators");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}
