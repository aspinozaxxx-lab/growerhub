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
import ru.growerhub.backend.mqtt.MqttMessageLog;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "MQTT_HOST=")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminMqttControllerIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MqttMessageLog messageLog;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        messageLog.clear();
        clearDatabase();
    }

    @Test
    void listMessagesRequiresAdmin() {
        UserEntity user = createUser("mqtt-user@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/mqtt/messages")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void listMessagesReturnsFilteredRecentMessages() {
        UserEntity admin = createUser("mqtt-admin@example.com", "admin");
        String token = buildToken(admin.getId());

        messageLog.recordInbound(
                "gh/dev/device-a/state",
                "{\"soil_moisture\":42}".getBytes(StandardCharsets.UTF_8),
                "state"
        );
        messageLog.recordInbound(
                "gh/dev/device-b/events",
                "{\"type\":\"SENSOR_READ_ERROR\"}".getBytes(StandardCharsets.UTF_8),
                "event"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("topic", "state")
                .queryParam("sender", "device-a")
                .when()
                .get("/api/admin/mqtt/messages")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].topic", equalTo("gh/dev/device-a/state"))
                .body("[0].sender", equalTo("device-a"))
                .body("[0].kind", equalTo("state"))
                .body("[0].direction", equalTo("in"));
    }

    @Test
    void listMessagesWithoutFiltersReturnsWholeBuffer() {
        UserEntity admin = createUser("mqtt-admin-all@example.com", "admin");
        String token = buildToken(admin.getId());

        messageLog.recordInbound(
                "unknown/topic",
                "raw".getBytes(StandardCharsets.UTF_8),
                "raw"
        );
        messageLog.recordInbound(
                "gh/dev/device-a/state",
                "{\"soil_moisture\":42}".getBytes(StandardCharsets.UTF_8),
                "state"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/mqtt/messages")
                .then()
                .statusCode(200)
                .body("", hasSize(2))
                .body("[0].topic", equalTo("gh/dev/device-a/state"))
                .body("[1].topic", equalTo("unknown/topic"));
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
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}
