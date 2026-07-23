package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.mqtt.MqttMessageHandler;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.contract.ZigbeeBrokerCredentialGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "MQTT_HOST=",
            "SELF_SERVICE_ENABLED=true",
            "zigbee.self-service.credentialCooldownSeconds=0"
        }
)
class SelfServiceTenantIsolationIntegrationTest extends IntegrationTestBase {
    private static final String SHARED_IEEE = "0xa4c1380000000001";
    private static final String SHARED_NAME = "shared_plug";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MqttMessageHandler mqttMessageHandler;

    @MockBean
    private ZigbeeBrokerCredentialGateway credentialGateway;

    @MockBean
    private ZigbeeCommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void sameIeeeAndFriendlyNameStayInsideCoordinatorNamespace() {
        UserEntity first = createUser("tenant-one@example.com");
        UserEntity second = createUser("tenant-two@example.com");
        String firstToken = buildToken(first.getId());
        String secondToken = buildToken(second.getId());

        Coordinator firstCoordinator = createCoordinator(firstToken, "First farm");
        Coordinator secondCoordinator = createCoordinator(secondToken, "Second farm");
        seedPlug(firstCoordinator, "OFF", 12.5);
        seedPlug(secondCoordinator, "ON", 42.0);

        given()
                .header("Authorization", "Bearer " + firstToken)
                .when()
                .get("/api/zigbee/coordinators/" + firstCoordinator.id() + "/overview")
                .then()
                .statusCode(200)
                .body("devices", hasSize(1))
                .body("devices[0].ieee_address", equalTo(SHARED_IEEE))
                .body("devices[0].state.state", equalTo("OFF"));

        given()
                .header("Authorization", "Bearer " + secondToken)
                .when()
                .get("/api/zigbee/coordinators/" + secondCoordinator.id() + "/overview")
                .then()
                .statusCode(200)
                .body("devices", hasSize(1))
                .body("devices[0].state.state", equalTo("ON"));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .queryParam("property", "power")
                .when()
                .get("/api/zigbee/coordinators/" + firstCoordinator.id()
                        + "/devices/" + SHARED_IEEE + "/history")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].value", equalTo(12.5f));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("{\"state\":\"ON\"}")
                .when()
                .post("/api/zigbee/coordinators/" + firstCoordinator.id()
                        + "/devices/" + SHARED_IEEE + "/set-state")
                .then()
                .statusCode(200)
                .body("topic", equalTo(firstCoordinator.baseTopic() + "/" + SHARED_NAME + "/set"));

        verify(commandGateway).publishSet(firstCoordinator.baseTopic(), SHARED_NAME, Map.of("state", "ON"));
        verify(commandGateway, never()).publishSet(secondCoordinator.baseTopic(), SHARED_NAME, Map.of("state", "ON"));
    }

    @Test
    void foreignCoordinatorAndZoneAlwaysLookMissing() {
        UserEntity first = createUser("owner-one@example.com");
        UserEntity second = createUser("owner-two@example.com");
        String firstToken = buildToken(first.getId());
        String secondToken = buildToken(second.getId());
        Coordinator firstCoordinator = createCoordinator(firstToken, "First");
        Coordinator secondCoordinator = createCoordinator(secondToken, "Second");
        seedPlug(firstCoordinator, "OFF", 1.0);
        seedPlug(secondCoordinator, "OFF", 2.0);

        assertNotFound(firstToken, "/api/zigbee/coordinators/" + secondCoordinator.id());
        assertNotFound(firstToken, "/api/zigbee/coordinators/" + secondCoordinator.id() + "/overview");

        given()
                .header("Authorization", "Bearer " + firstToken)
                .queryParam("property", "power")
                .when()
                .get("/api/zigbee/coordinators/" + secondCoordinator.id()
                        + "/devices/" + SHARED_IEEE + "/history")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Koordinator ne naiden"));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("{\"state\":\"ON\"}")
                .when()
                .post("/api/zigbee/coordinators/" + secondCoordinator.id()
                        + "/devices/" + SHARED_IEEE + "/set-state")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Koordinator ne naiden"));

        Integer firstZone = createZone(firstToken, "Zone one");
        Integer secondZone = createZone(secondToken, "Zone two");
        Integer firstSection = createSection(firstToken, firstZone, "Section one");

        given()
                .header("Authorization", "Bearer " + firstToken)
                .when()
                .get("/api/automation")
                .then()
                .statusCode(200)
                .body("rooms", hasSize(1))
                .body("rooms[0].id", equalTo(firstZone));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("{\"name\":\"stolen\"}")
                .when()
                .put("/api/automation/zones/" + secondZone)
                .then()
                .statusCode(404)
                .body("detail", equalTo("pomeshchenie ne naideno"));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("""
                        {"resources":[{
                          "role":"LIGHT_SWITCH",
                          "source_type":"ZIGBEE_DEVICE",
                          "zigbee_coordinator_id":"%s",
                          "zigbee_ieee_address":"%s",
                          "zigbee_property":"state"
                        }]}
                        """.formatted(secondCoordinator.id(), SHARED_IEEE))
                .when()
                .put("/api/automation/sections/" + firstSection + "/resources")
                .then()
                .statusCode(404);
    }

    private Coordinator createCoordinator(String token, String name) {
        var response = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", name))
                .when()
                .post("/api/zigbee/coordinators")
                .then()
                .statusCode(201)
                .header("Cache-Control", "no-store")
                .extract();
        return new Coordinator(
                response.path("coordinator.id"),
                response.path("setup.username"),
                response.path("setup.base_topic")
        );
    }

    private Integer createZone(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", name))
                .when()
                .post("/api/automation/zones")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].id");
    }

    private Integer createSection(String token, Integer zoneId, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", name))
                .when()
                .post("/api/automation/zones/" + zoneId + "/sections")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].boxes[0].id");
    }

    private void seedPlug(Coordinator coordinator, String state, double power) {
        String device = """
                [{
                  "friendly_name":"%s",
                  "ieee_address":"%s",
                  "type":"Router",
                  "supported":true,
                  "definition":{
                    "model":"Synthetic plug",
                    "exposes":[
                      {"type":"binary","name":"state","property":"state","access":7,
                       "value_on":"ON","value_off":"OFF"},
                      {"type":"numeric","name":"power","property":"power","access":1,"unit":"W"}
                    ]
                  }
                }]
                """.formatted(SHARED_NAME, SHARED_IEEE);
        mqttMessageHandler.handleInboundMessage(
                coordinator.baseTopic() + "/bridge/devices",
                device.getBytes(StandardCharsets.UTF_8)
        );
        String payload = "{\"state\":\"" + state + "\",\"power\":" + power + "}";
        mqttMessageHandler.handleInboundMessage(
                coordinator.baseTopic() + "/" + SHARED_NAME,
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void assertNotFound(String token, String path) {
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(path)
                .then()
                .statusCode(404)
                .body("detail", equalTo("Koordinator ne naiden"));
    }

    private UserEntity createUser(String email) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return userRepository.save(UserEntity.create(email, null, "user", true, now, now));
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

    private record Coordinator(String id, String mqttUsername, String baseTopic) {
    }
}
