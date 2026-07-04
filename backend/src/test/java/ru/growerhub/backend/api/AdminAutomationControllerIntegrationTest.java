package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MQTT_HOST="
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AdminAutomationControllerIntegrationTest.TestPublisherConfig.class)
class AdminAutomationControllerIntegrationTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZigbeeFacade zigbeeFacade;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        seedZigbee();
    }

    @Test
    void overviewRequiresAdmin() {
        UserEntity user = createUser("automation-user@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/automation")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void createsRoomAndBoxWithDisabledDefaultScenarios() {
        UserEntity admin = createUser("automation-admin@example.com", "admin");
        String token = buildToken(admin.getId());

        Integer roomId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Room A\"}")
                .when()
                .post("/api/admin/automation/rooms")
                .then()
                .statusCode(200)
                .body("rooms", hasSize(1))
                .body("rooms[0].name", equalTo("Room A"))
                .body("rooms[0].scenarios[0].scenario_type", equalTo("ROOM_CLIMATE"))
                .body("rooms[0].scenarios[0].enabled", equalTo(false))
                .extract()
                .path("rooms[0].id");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Box A\"}")
                .when()
                .post("/api/admin/automation/rooms/" + roomId + "/boxes")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes", hasSize(1))
                .body("rooms[0].boxes[0].name", equalTo("Box A"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("BOX_CLIMATE"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("LIGHT_SCHEDULE"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("WATERING"))
                .body("rooms[0].boxes[0].scenarios.find { it.scenario_type == 'LIGHT_SCHEDULE' }.enabled", equalTo(false));
    }

    @Test
    void lightResourceAcceptsOnlyZigbeeWritableState() {
        UserEntity admin = createUser("automation-zigbee-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room B");
        Integer boxId = createBox(token, roomId, "Box B");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"LIGHT_SWITCH","source_type":"NATIVE_PUMP","native_pump_id":1}
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(400)
                .body("detail", equalTo("LIGHT_SWITCH v v1 dolzhen byt' Zigbee switch"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"LIGHT_SWITCH",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df3",
                            "zigbee_property":"state",
                            "command_property":"state",
                            "on_value":"ON",
                            "off_value":"OFF"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].role", equalTo("LIGHT_SWITCH"))
                .body("rooms[0].boxes[0].resources[0].source_type", equalTo("ZIGBEE_DEVICE"))
                .body("rooms[0].boxes[0].resources[0].zigbee_ieee_address", equalTo("0xa4c13895af2c1df3"))
                .body("rooms[0].boxes[0].resources[0].zigbee_property", equalTo("state"))
                .body("rooms[0].boxes[0].resources[0].command_property", equalTo("state"))
                .body("rooms[0].boxes[0].resources[0].ready", equalTo(true))
                .body("rooms[0].boxes[0].readiness.LIGHT_SCHEDULE.ready", equalTo(true));
    }

    private Integer createRoom(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post("/api/admin/automation/rooms")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].id");
    }

    private Integer createBox(String token, Integer roomId, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post("/api/admin/automation/rooms/" + roomId + "/boxes")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].boxes[0].id");
    }

    private void seedZigbee() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.BRIDGE_DEVICES,
                "zigbee2growerhub/bridge/devices",
                "bridge/devices",
                null,
                "[]",
                List.of(smartPlugBridgeDevice()),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/smartplug1",
                "smartplug1",
                "smartplug1",
                "{\"state\":\"OFF\",\"power\":0}",
                Map.of("state", "OFF", "power", 0),
                now
        ));
    }

    private Map<String, Object> smartPlugBridgeDevice() {
        return Map.of(
                "friendly_name", "smartplug1",
                "ieee_address", "0xa4c13895af2c1df3",
                "type", "Router",
                "supported", true,
                "definition", Map.of(
                        "model", "TS011F_plug_1_1",
                        "vendor", "Zbeacon",
                        "exposes", List.of(
                                Map.of("type", "switch", "features", List.of(
                                        Map.of(
                                                "type", "binary",
                                                "name", "state",
                                                "property", "state",
                                                "access", 7,
                                                "value_on", "ON",
                                                "value_off", "OFF",
                                                "label", "State"
                                        )
                                )),
                                Map.of(
                                        "type", "numeric",
                                        "name", "power",
                                        "property", "power",
                                        "access", 1,
                                        "unit", "W",
                                        "label", "Power"
                                )
                        )
                )
        );
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
                .setExpiration(Date.from(java.time.Instant.now().plusSeconds(3600)))
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
        jdbcTemplate.update("DELETE FROM zigbee_command_response_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_bridge_snapshots");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    @TestConfiguration
    static class TestPublisherConfig {
        @Bean
        TestPublisher testPublisher() {
            return new TestPublisher();
        }
    }

    static class TestPublisher implements MqttPublisher {
        @Override
        public void publishCmd(String deviceId, Object cmd) {
        }

        @Override
        public void publishJson(String topic, Object payload, int qos, boolean retained) {
        }
    }
}
