package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
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
                .body("devices.find { it.friendly_name == 'smartplug1' }.availability", equalTo("online"));
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
                .body("{\"friendly_name\":\"plug-main\"}")
                .when()
                .post("/api/admin/zigbee/devices/0xa4c13895af2c1df3/rename")
                .then()
                .statusCode(200)
                .body("topic", equalTo("zigbee2growerhub/bridge/request/device/rename"));

        List<PublishedJson> published = testPublisher.getPublished();
        Assertions.assertEquals(3, published.size());
        Assertions.assertEquals("zigbee2growerhub/bridge/request/permit_join", published.get(0).topic());
        Assertions.assertEquals(120, ((Map<?, ?>) published.get(0).payload()).get("time"));
        Assertions.assertEquals("zigbee2growerhub/smartplug1/set", published.get(1).topic());
        Assertions.assertEquals("ON", ((Map<?, ?>) published.get(1).payload()).get("state"));
        Assertions.assertEquals("zigbee2growerhub/bridge/request/device/rename", published.get(2).topic());
        Assertions.assertEquals("smartplug1", ((Map<?, ?>) published.get(2).payload()).get("from"));
        Assertions.assertEquals("plug-main", ((Map<?, ?>) published.get(2).payload()).get("to"));
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
                        Map.of("friendly_name", "smartplug1", "ieee_address", "0xa4c13895af2c1df3", "type", "Router", "supported", true)
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
