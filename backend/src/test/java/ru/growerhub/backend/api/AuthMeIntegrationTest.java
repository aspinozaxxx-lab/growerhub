package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthMeIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void missingAuthorizationReturns401() {
        given()
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void invalidJwtReturns401() {
        given()
                .header("Authorization", "Bearer not-a-jwt")
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Ne udalos' raspoznavat' token"));
    }

    @Test
    void missingUserIdClaimReturns401() {
        String token = buildToken(Map.of("sub", "test"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Ne udalos' raspoznavat' token"));
    }

    @Test
    void userIdNotIntReturns401() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", "abc");
        String token = buildToken(claims);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Ne udalos' raspoznavat' token"));
    }

    @Test
    void userNotFoundReturns401() {
        String token = buildToken(Map.of("user_id", 999));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Polzovatel' ne najden"));
    }

    @Test
    void inactiveUserReturns403() {
        insertUser(2, "inactive@example.com", null, "user", false, null, null);
        String token = buildToken(Map.of("user_id", 2));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(403)
                .header("WWW-Authenticate", nullValue())
                .body("detail", equalTo("Polzovatel' otkljuchen"));
    }

    @Test
    void usernameNullReturns200() {
        insertUser(1, "active@example.com", null, "user", true, LocalDateTime.now(), LocalDateTime.now());
        String token = buildToken(Map.of("user_id", 1));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("email", equalTo("active@example.com"))
                .body("username", nullValue())
                .body("role", equalTo("user"))
                .body("is_active", equalTo(true));
    }

    @Test
    void createdAtAndUpdatedAtNullReturns200() {
        insertUser(3, "timestamps@example.com", "user-ts", "user", true, null, null);
        String token = buildToken(Map.of("user_id", 3));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("id", equalTo(3))
                .body("email", equalTo("timestamps@example.com"))
                .body("username", equalTo("user-ts"))
                .body("created_at", nullValue())
                .body("updated_at", nullValue());
    }

    private void insertUser(
            int id,
            String email,
            String username,
            String role,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, username, role, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                email,
                username,
                role,
                isActive,
                toTimestamp(createdAt),
                toTimestamp(updatedAt)
        );
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private String buildToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(new HashMap<>(claims))
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
