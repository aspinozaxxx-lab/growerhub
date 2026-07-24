package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItems;

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
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MQTT_HOST="
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AdminZigbeeControllerIntegrationTest.TestPublisherConfig.class)
class AdminZigbeeControllerIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZigbeeFacade zigbeeFacade;

    @Autowired
    private TestPublisher testPublisher;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        testPublisher.clear();
        clearDatabase();
        seedZigbee();
    }

    @Test
    void overviewRequiresAdmin() {
        UserEntity user = createUser("zigbee-user@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/zigbee")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void historyRequiresAdmin() {
        UserEntity user = createUser("zigbee-history-user@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("property", "state")
                .when()
                .get("/api/admin/zigbee/devices/0xa4c13895af2c1df3/history")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void overviewReturnsStoredCoordinatorAndDevices() {
        UserEntity admin = createUser("zigbee-admin@example.com", "admin");
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/zigbee")
                .then()
                .statusCode(200)
                .body("bridge.state", equalTo("online"))
                .body("coordinator.ieee_address", equalTo("0x00124b002c7a2966"))
                .body("devices", hasSize(2))
                .body("devices.find { it.friendly_name == 'smartplug1' }.state.state", equalTo("OFF"))
                .body("devices.find { it.friendly_name == 'smartplug1' }.availability", equalTo("online"))
                .body("devices.find { it.friendly_name == 'smartplug1' }.definition.model", equalTo("TS011F_plug_1_1"))
                .body("devices.find { it.friendly_name == 'smartplug1' }.image_url",
                        equalTo("https://www.zigbee2mqtt.io/images/devices/TS011F_plug_1_1.png"))
                .body("devices.find { it.friendly_name == 'smartplug1' }.metrics.property",
                        hasItems("state", "power", "current", "voltage", "energy", "linkquality"))
                .body("devices.find { it.friendly_name == 'smartplug1' }.controls.property",
                        hasItems("state", "countdown", "power_outage_memory", "child_lock", "identify"));
    }

    @Test
    void historyReturnsBinaryAndNumericPropertyReadings() {
        UserEntity admin = createUser("zigbee-history-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/smartplug1",
                "smartplug1",
                "smartplug1",
                "{\"state\":\"ON\",\"power\":15.5,\"nested\":{\"ignored\":true}}",
                Map.of("state", "ON", "power", 15.5, "nested", Map.of("ignored", true)),
                now
        ));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("property", "state")
                .queryParam("hours", 24)
                .when()
                .get("/api/admin/zigbee/devices/0xa4c13895af2c1df3/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].value", equalTo(0.0f))
                .body("[0].raw_value", equalTo("OFF"))
                .body("[1].value", equalTo(1.0f))
                .body("[1].raw_value", equalTo("ON"));

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("property", "power")
                .queryParam("hours", 24)
                .when()
                .get("/api/admin/zigbee/devices/0xa4c13895af2c1df3/history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[1].value", equalTo(15.5f));
    }

    @Test
    void historyPreservesFrequentBinaryTransitionsForDailyStats() {
        UserEntity admin = createUser("zigbee-transition-history-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        LocalDateTime firstTransitionAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(167);
        for (int index = 0; index < 315; index++) {
            String state = index % 2 == 0 ? "ON" : "OFF";
            zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                    ZigbeeMqttMessageType.DEVICE_STATE,
                    "zigbee2growerhub/smartplug1",
                    "smartplug1",
                    "smartplug1",
                    "{\"state\":\"" + state + "\"}",
                    Map.of("state", state),
                    firstTransitionAt.plusMinutes(index * 30L)
            ));
        }

        List<Float> values = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("property", "state")
                .queryParam("hours", 168)
                .when()
                .get("/api/admin/zigbee/devices/0xa4c13895af2c1df3/history")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("value", Float.class);

        Assertions.assertEquals(316, values.size());
        Assertions.assertTrue(values.contains(0.0f));
        Assertions.assertTrue(values.contains(1.0f));
    }

    @Test
    void publishesPermitJoinSetStateAndRenameCommands() {
        UserEntity admin = createUser("zigbee-admin-actions@example.com", "admin");
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"seconds\":120}")
                .when()
                .post("/api/admin/zigbee/permit-join")
                .then()
                .statusCode(200)
                .body("topic", equalTo("zigbee2growerhub/bridge/request/permit_join"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"state\":\"ON\"}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/set-state")
                .then()
                .statusCode(200)
                .body("topic", equalTo("zigbee2growerhub/smartplug1/set"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"property\":\"child_lock\",\"value\":true}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/set")
                .then()
                .statusCode(200)
                .body("topic", equalTo("zigbee2growerhub/smartplug1/set"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"friendly_name\":\"plug-main\"}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/rename")
                .then()
                .statusCode(200)
                .body("topic", equalTo("zigbee2growerhub/bridge/request/device/rename"));

        List<PublishedJson> published = testPublisher.getPublished();
        Assertions.assertEquals(4, published.size());
        Assertions.assertEquals("zigbee2growerhub/bridge/request/permit_join", published.get(0).topic());
        Assertions.assertEquals(120, ((Map<?, ?>) published.get(0).payload()).get("time"));
        Assertions.assertEquals("zigbee2growerhub/smartplug1/set", published.get(1).topic());
        Assertions.assertEquals("ON", ((Map<?, ?>) published.get(1).payload()).get("state"));
        Assertions.assertEquals("zigbee2growerhub/smartplug1/set", published.get(2).topic());
        Assertions.assertEquals(true, ((Map<?, ?>) published.get(2).payload()).get("child_lock"));
        Assertions.assertEquals("zigbee2growerhub/bridge/request/device/rename", published.get(3).topic());
        Assertions.assertEquals("smartplug1", ((Map<?, ?>) published.get(3).payload()).get("from"));
        Assertions.assertEquals("plug-main", ((Map<?, ?>) published.get(3).payload()).get("to"));
    }

    @Test
    void genericSetRejectsUnknownAndReadOnlyProperties() {
        UserEntity admin = createUser("zigbee-admin-reject@example.com", "admin");
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"property\":\"power\",\"value\":10}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/set")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Свойство Zigbee недоступно для записи"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"property\":\"unknown\",\"value\":\"ON\"}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/set")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Свойство Zigbee недоступно для записи"));
    }

    private void seedZigbee() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.BRIDGE_STATE,
                "zigbee2growerhub/bridge/state",
                "bridge/state",
                null,
                "{\"state\":\"online\"}",
                Map.of("state", "online"),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.BRIDGE_DEVICES,
                "zigbee2growerhub/bridge/devices",
                "bridge/devices",
                null,
                "[]",
                List.of(
                        Map.of("friendly_name", "Coordinator", "ieee_address", "0x00124b002c7a2966", "type", "Coordinator", "supported", true),
                        smartPlugBridgeDevice()
                ),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_AVAILABILITY,
                "zigbee2growerhub/smartplug1/availability",
                "smartplug1/availability",
                "smartplug1",
                "{\"state\":\"online\"}",
                Map.of("state", "online"),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/smartplug1",
                "smartplug1",
                "smartplug1",
                "{\"state\":\"OFF\",\"power\":0,\"current\":0,\"voltage\":221,\"energy\":1.5,\"linkquality\":120}",
                Map.of("state", "OFF", "power", 0, "current", 0, "voltage", 221, "energy", 1.5, "linkquality", 120),
                now
        ));
    }

    private Map<String, Object> smartPlugBridgeDevice() {
        return Map.of(
                "friendly_name", "smartplug1",
                "ieee_address", "0xa4c13895af2c1df3",
                "type", "Router",
                "supported", true,
                "disabled", false,
                "definition", smartPlugDefinition()
        );
    }

    private Map<String, Object> smartPlugDefinition() {
        return Map.of(
                "model", "TS011F_plug_1_1",
                "vendor", "Zbeacon",
                "description", "Smart plug (with power monitoring)",
                "exposes", smartPlugExposes()
        );
    }

    private List<Object> smartPlugExposes() {
        return List.of(
                Map.of("type", "switch", "features", List.of(
                        Map.of(
                                "type", "binary",
                                "name", "state",
                                "property", "state",
                                "access", 7,
                                "value_on", "ON",
                                "value_off", "OFF",
                                "description", "On/off state of the switch",
                                "label", "State"
                        )
                )),
                Map.of(
                        "type", "numeric",
                        "name", "countdown",
                        "property", "countdown",
                        "access", 3,
                        "unit", "s",
                        "value_min", 0,
                        "value_max", 43200,
                        "label", "Countdown"
                ),
                Map.of(
                        "type", "enum",
                        "name", "power_outage_memory",
                        "property", "power_outage_memory",
                        "access", 7,
                        "values", List.of("on", "off", "restore"),
                        "label", "Power outage memory"
                ),
                Map.of(
                        "type", "numeric",
                        "name", "power",
                        "property", "power",
                        "access", 1,
                        "unit", "W",
                        "label", "Power"
                ),
                Map.of(
                        "type", "numeric",
                        "name", "current",
                        "property", "current",
                        "access", 1,
                        "unit", "A",
                        "label", "Current"
                ),
                Map.of(
                        "type", "numeric",
                        "name", "voltage",
                        "property", "voltage",
                        "access", 1,
                        "unit", "V",
                        "label", "Voltage"
                ),
                Map.of(
                        "type", "numeric",
                        "name", "energy",
                        "property", "energy",
                        "access", 1,
                        "unit", "kWh",
                        "label", "Energy"
                ),
                Map.of(
                        "type", "binary",
                        "name", "child_lock",
                        "property", "child_lock",
                        "access", 3,
                        "value_on", true,
                        "value_off", false,
                        "label", "Child lock"
                ),
                Map.of(
                        "type", "enum",
                        "name", "identify",
                        "property", "identify",
                        "access", 2,
                        "values", List.of("identify"),
                        "label", "Identify"
                ),
                Map.of(
                        "type", "numeric",
                        "name", "linkquality",
                        "property", "linkquality",
                        "access", 1,
                        "unit", "lqi",
                        "value_min", 0,
                        "value_max", 255,
                        "label", "Linkquality"
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
        jdbcTemplate.update("DELETE FROM zigbee_device_property_readings");
        jdbcTemplate.update("DELETE FROM zigbee_device_state_events");
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

    record PublishedJson(String topic, Object payload, int qos, boolean retained) {
    }

    static class TestPublisher implements MqttPublisher {
        private final List<PublishedJson> published = new ArrayList<>();

        @Override
        public void publishCmd(String deviceId, Object cmd) {
        }

        @Override
        public void publishJson(String topic, Object payload, int qos, boolean retained) {
            published.add(new PublishedJson(topic, payload, qos, retained));
        }

        void clear() {
            published.clear();
        }

        List<PublishedJson> getPublished() {
            return published;
        }
    }
}
