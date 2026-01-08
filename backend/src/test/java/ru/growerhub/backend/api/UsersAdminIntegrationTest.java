package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.device.DeviceRepository;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersAdminIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository identityRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void listUsersRequiresAuth() {
        given()
                .when()
                .get("/api/users")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void listUsersRequiresAdmin() {
        UserEntity user = createUser("user@example.com", "user", true);
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/users")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void createUserReturns201() {
        UserEntity admin = createUser("admin@example.com", "admin", true);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "new@example.com");
        payload.put("username", "neo");
        payload.put("role", "user");
        payload.put("password", "secret");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/users")
                .then()
                .statusCode(201)
                .body("email", equalTo("new@example.com"))
                .body("role", equalTo("user"))
                .body("id", notNullValue());

        UserAuthIdentityEntity identity = identityRepository.findAll().stream().findFirst().orElse(null);
        Assertions.assertNotNull(identity);
        Assertions.assertEquals("local", identity.getProvider());
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        UserEntity admin = createUser("admin2@example.com", "admin", true);
        createUser("dup@example.com", "user", true);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "dup@example.com");
        payload.put("password", "secret");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/users")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Polzovatel' s takim email uzhe sushhestvuet"));
    }

    @Test
    void getUserNotFoundReturns404() {
        UserEntity admin = createUser("admin3@example.com", "admin", true);
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/users/9999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Polzovatel' ne najden"));
    }

    @Test
    void updateUserAppliesFields() {
        UserEntity admin = createUser("admin4@example.com", "admin", true);
        UserEntity target = createUser("target@example.com", "user", true);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", "newname");
        payload.put("role", "admin");
        payload.put("is_active", false);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .patch("/api/users/" + target.getId())
                .then()
                .statusCode(200)
                .body("username", equalTo("newname"))
                .body("role", equalTo("admin"))
                .body("is_active", equalTo(false));
    }

    @Test
    void deleteUserUnassignsDevices() {
        UserEntity admin = createUser("admin5@example.com", "admin", true);
        UserEntity target = createUser("device-owner@example.com", "user", true);
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("dev-delete");
        device.setName("Device dev-delete");
        device.setUser(target);
        deviceRepository.save(device);
        UserAuthIdentityEntity identity = UserAuthIdentityEntity.create(
                target,
                "local",
                null,
                "hash",
                LocalDateTime.now(ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC)
        );
        identityRepository.save(identity);
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/users/" + target.getId())
                .then()
                .statusCode(204);

        DeviceEntity stored = deviceRepository.findById(device.getId()).orElse(null);
        Assertions.assertNotNull(stored);
        Assertions.assertNull(stored.getUser());
    }

    @Test
    void createUserRequiresFields() {
        UserEntity admin = createUser("admin6@example.com", "admin", true);
        String token = buildToken(admin.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "missing@example.com");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/users")
                .then()
                .statusCode(422);
    }

    @Test
    void getUserInvalidIdReturns422() {
        UserEntity admin = createUser("admin7@example.com", "admin", true);
        String token = buildToken(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/users/not-a-number")
                .then()
                .statusCode(422);
    }

    private UserEntity createUser(String email, String role, boolean active) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, role, active, now, now);
        return userRepository.save(user);
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

