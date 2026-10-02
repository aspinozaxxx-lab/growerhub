package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.contract.DeviceBrokerCredentialGateway;
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
    private DeviceBrokerCredentialGateway deviceCredentialGateway;

    @MockBean
    private ZigbeeCommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void sameIeeeAndFriendlyNameStayInsideCoordinatorNamespace(String role) {
        UserEntity first = createUser("tenant-one@example.com", role);
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

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void foreignCoordinatorAndZoneAlwaysLookMissing(String role) {
        UserEntity first = createUser("owner-one@example.com", role);
        UserEntity second = createUser("owner-two@example.com");
        String firstToken = buildToken(first.getId());
        String secondToken = buildToken(second.getId());
        Coordinator firstCoordinator = createCoordinator(firstToken, "First");
        Coordinator secondCoordinator = createCoordinator(secondToken, "Second");
        seedPlug(firstCoordinator, "OFF", 1.0);
        seedPlug(secondCoordinator, "OFF", 2.0);
        clearInvocations(commandGateway, credentialGateway);

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
                .body("detail", equalTo("Координатор не найден"));

        for (var request : Map.of(
                "/permit-join", "{\"seconds\":60}",
                "/credentials/rotate", "{}",
                "/devices/" + SHARED_IEEE + "/set", "{\"property\":\"state\",\"value\":\"ON\"}",
                "/devices/" + SHARED_IEEE + "/rename", "{\"friendly_name\":\"stolen\"}"
        ).entrySet()) {
            given()
                    .header("Authorization", "Bearer " + firstToken)
                    .contentType("application/json")
                    .body(request.getValue())
                    .when()
                    .post("/api/zigbee/coordinators/" + secondCoordinator.id() + request.getKey())
                    .then()
                    .statusCode(404)
                    .body("detail", equalTo("Координатор не найден"));
        }

        given()
                .header("Authorization", "Bearer " + firstToken)
                .when()
                .delete("/api/zigbee/coordinators/" + secondCoordinator.id())
                .then()
                .statusCode(404)
                .body("detail", equalTo("Координатор не найден"));
        verifyNoInteractions(commandGateway, credentialGateway);

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("{\"state\":\"ON\"}")
                .when()
                .post("/api/zigbee/coordinators/" + secondCoordinator.id()
                        + "/devices/" + SHARED_IEEE + "/set-state")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Координатор не найден"));

        createFarm(firstToken, "Farm one");
        createFarm(secondToken, "Farm two");
        Integer firstZone = createZone(firstToken, "Zone one");
        Integer secondZone = createZone(secondToken, "Zone two");

        given()
                .header("Authorization", "Bearer " + firstToken)
                .when()
                .get("/api/automation/farm")
                .then()
                .statusCode(200)
                .body("farm.zones", hasSize(1))
                .body("farm.zones[0].id", equalTo(firstZone));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("{\"name\":\"stolen\"}")
                .when()
                .put("/api/automation/farm/zones/" + secondZone)
                .then()
                .statusCode(404)
                .body("detail", equalTo("Теплица не найдена"));

        given()
                .header("Authorization", "Bearer " + firstToken)
                .contentType("application/json")
                .body("""
                        {"slots":[{
                          "role":"LIGHT_SWITCH",
                          "source_type":"ZIGBEE_DEVICE",
                          "zigbee_coordinator_id":"%s",
                          "zigbee_ieee_address":"%s",
                          "zigbee_property":"state"
                        }]}
                        """.formatted(secondCoordinator.id(), SHARED_IEEE))
                .when()
                .put("/api/automation/farm/zones/" + firstZone + "/slots")
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void personalCatalogExcludesForeignPhysicalAndSimulatedDevices(String role) {
        UserEntity owner = createUser("catalog-owner@example.com", role);
        UserEntity foreign = createUser("catalog-foreign@example.com");
        String token = buildToken(owner.getId());
        String foreignToken = buildToken(foreign.getId());
        Coordinator own = createCoordinator(token, "Own coordinator");
        Coordinator other = createCoordinator(foreignToken, "Foreign coordinator");
        Coordinator simulated = createCoordinator(foreignToken, "Foreign demo coordinator");
        seedPlug(own, "OFF", 12.5);
        seedPlug(other, "ON", 42.0);
        seedPlug(simulated, "ON", 100.0);
        jdbcTemplate.update("UPDATE zigbee_coordinators SET execution_kind='SIMULATED' WHERE public_id=?",
                java.util.UUID.fromString(simulated.id()));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/zigbee/coordinators")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(own.id()));

        for (String path : java.util.List.of("/api/automation/farms", "/api/automation/farm")) {
            given()
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200)
                    .body("resource_catalog.zigbee_devices", hasSize(1))
                    .body("resource_catalog.zigbee_devices[0].coordinator_id", equalTo(own.id()))
                    .body("resource_catalog.zigbee_devices[0].coordinator_name", equalTo("Own coordinator"))
                    .body("resource_catalog.zigbee_devices[0].ieee_address", equalTo(SHARED_IEEE))
                    .body("resource_catalog.zigbee_devices[0].metrics.find { it.property == 'power' }.value", equalTo(12.5f));
        }

        var adminHistory = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("property", "power")
                .when()
                .get("/api/admin/zigbee/coordinators/" + other.id() + "/devices/" + SHARED_IEEE + "/history")
                .then()
                .statusCode("admin".equals(role) ? 200 : 403);
        if ("admin".equals(role)) {
            adminHistory.body("$", hasSize(1)).body("[0].value", equalTo(42.0f));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void personalCatalogIsEmptyWithoutOwnedCoordinator(String role) {
        UserEntity owner = createUser("empty-owner@example.com", role);
        UserEntity foreign = createUser("empty-foreign@example.com");
        Coordinator other = createCoordinator(buildToken(foreign.getId()), "Foreign coordinator");
        seedPlug(other, "ON", 42.0);

        given()
                .header("Authorization", "Bearer " + buildToken(owner.getId()))
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms", hasSize(0))
                .body("resource_catalog.zigbee_devices", hasSize(0));
    }

    @Test
    void legacyAutomationSelfServiceApiIsRemoved() {
        UserEntity owner = createUser("legacy-api@example.com");
        String token = buildToken(owner.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation")
                .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Removed"))
                .when()
                .post("/api/automation/zones")
                .then()
                .statusCode(404);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("resources", java.util.List.of()))
                .when()
                .put("/api/automation/sections/1/resources")
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

    @ParameterizedTest
    @ValueSource(strings = {"user", "admin"})
    void waterStatisticsKeepRepeatedReportsAndSameIeeeInsideOwner(String role) {
        UserEntity first = createUser("water-one@example.com", role);
        UserEntity second = createUser("water-two@example.com");
        String token = buildToken(first.getId());
        Coordinator own = createCoordinator(token, "Own water");
        Coordinator foreign = createCoordinator(buildToken(second.getId()), "Other water");
        long end = java.time.Instant.now().minusSeconds(30).getEpochSecond();
        for (var entry : Map.of(own, 42, foreign, 250).entrySet()) {
            mqttMessageHandler.handleInboundMessage(entry.getKey().baseTopic() + "/bridge/devices", """
                    [{"friendly_name":"shared_plug","ieee_address":"%s","type":"Router","definition":{
                    "model":"SWV","vendor":"SONOFF","exposes":[
                    {"type":"numeric","property":"real_time_irrigation_volume","access":1,"unit":"L"},
                    {"type":"numeric","property":"irrigation_start_time","access":1},
                    {"type":"numeric","property":"irrigation_end_time","access":1},
                    {"type":"numeric","property":"flow","access":1,"unit":"m³/h"}]}}]
                    """.formatted(SHARED_IEEE).getBytes(StandardCharsets.UTF_8), false);
            byte[] report = """
                    {"state":"OFF","flow":0,"real_time_irrigation_volume":%s,
                    "irrigation_start_time":%s,"irrigation_end_time":%s}
                    """.formatted(entry.getValue(), end - 60, end).getBytes(StandardCharsets.UTF_8);
            mqttMessageHandler.handleInboundMessage(entry.getKey().baseTopic() + "/" + SHARED_NAME, report, false);
            mqttMessageHandler.handleInboundMessage(entry.getKey().baseTopic() + "/" + SHARED_NAME, report, false);
        }
        clearInvocations(commandGateway);
        String path = "/api/manual-watering/coordinators/" + own.id() + "/devices/" + SHARED_IEEE + "/water-statistics";
        given().header("Authorization", "Bearer " + token).when().get(path).then().statusCode(200)
                .body("known_volume_l", equalTo(42f)).body("operations", hasSize(1)).body("partial_volume", equalTo(true));
        assertNotFound(token, "/api/manual-watering/coordinators/" + foreign.id() + "/devices/" + SHARED_IEEE + "/water-statistics");
        given().when().get(path).then().statusCode(401);
        verifyNoInteractions(commandGateway);
    }

    private void createFarm(String token, String name) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", name))
                .when()
                .post("/api/automation/farm")
                .then()
                .statusCode(200);
    }

    private Integer createZone(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", name))
                .when()
                .post("/api/automation/farm/zones")
                .then()
                .statusCode(200)
                .extract()
                .path("farm.zones[0].id");
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
                device.getBytes(StandardCharsets.UTF_8), false
        );
        String payload = "{\"state\":\"" + state + "\",\"power\":" + power + "}";
        mqttMessageHandler.handleInboundMessage(
                coordinator.baseTopic() + "/" + SHARED_NAME,
                payload.getBytes(StandardCharsets.UTF_8), false
        );
    }

    private void assertNotFound(String token, String path) {
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get(path)
                .then()
                .statusCode(404)
                .body("detail", equalTo("Координатор не найден"));
    }

    private UserEntity createUser(String email) {
        return createUser(email, "user");
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
