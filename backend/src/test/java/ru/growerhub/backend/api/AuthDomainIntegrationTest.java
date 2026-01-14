package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.auth.engine.AuthSettings;
import ru.growerhub.backend.common.component.PasswordHasher;
import ru.growerhub.backend.auth.engine.RefreshTokenService;
import ru.growerhub.backend.auth.jpa.UserAuthIdentityEntity;
import ru.growerhub.backend.auth.jpa.UserAuthIdentityRepository;
import ru.growerhub.backend.auth.jpa.UserRefreshTokenEntity;
import ru.growerhub.backend.auth.jpa.UserRefreshTokenRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthDomainIntegrationTest extends IntegrationTestBase {

    private static final String PASSLIB_HASH =
            "$pbkdf2-sha256$29000$AQIDBAUGBwgJCgsMDQ4PEA$Y44huKS63zDb0ckparjOGpy6ParmA.WrWScxigLcMvQ";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthIdentityRepository identityRepository;

    @Autowired
    private UserRefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuthSettings authSettings;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void loginSuccessSetsRefreshCookie() {
        UserEntity user = createUser("user@example.com", true);
        createIdentity(user, "local", null, passwordHasher.hash("secret"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "user@example.com");
        payload.put("password", "secret");

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .header("Set-Cookie", containsString(authSettings.getRefreshTokenCookieName() + "="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .header("Set-Cookie", containsString("Path=" + authSettings.getRefreshTokenCookiePath()))
                .header("Set-Cookie", containsString("SameSite=" + authSettings.getRefreshTokenCookieSameSite()))
                .body("access_token", notNullValue())
                .body("token_type", equalTo("bearer"));
    }

    @Test
    void loginPasslibHashSupportsValidAndInvalidPassword() {
        UserEntity user = createUser("passlib@example.com", true);
        createIdentity(user, "local", null, PASSLIB_HASH);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "passlib@example.com");
        payload.put("password", "secret");

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200);

        payload.put("password", "wrong");

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Nevernyj email ili parol'"));
    }

    @Test
    void loginInvalidReturns401() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "missing@example.com");
        payload.put("password", "wrong");

        given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Nevernyj email ili parol'"));
    }

    @Test
    void refreshMissingCookieReturns401() {
        given()
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(401)
                .body("detail", equalTo("Refresh token required"));
    }

    @Test
    void refreshSuccessRotatesToken() {
        UserEntity user = createUser("refresh@example.com", true);
        String refreshRaw = refreshTokenService.generateToken();
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserRefreshTokenEntity record = UserRefreshTokenEntity.create(
                user.getId(),
                refreshHash,
                now,
                now.plusDays(30),
                "agent",
                "127.0.0.1"
        );
        refreshTokenRepository.save(record);

        given()
                .cookie(authSettings.getRefreshTokenCookieName(), refreshRaw)
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .header("Set-Cookie", containsString(authSettings.getRefreshTokenCookieName() + "="))
                .body("access_token", notNullValue())
                .body("token_type", equalTo("bearer"));
    }

    @Test
    void logoutRevokesToken() {
        UserEntity user = createUser("logout@example.com", true);
        String refreshRaw = refreshTokenService.generateToken();
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserRefreshTokenEntity record = UserRefreshTokenEntity.create(
                user.getId(),
                refreshHash,
                now,
                now.plusDays(30),
                "agent",
                "127.0.0.1"
        );
        refreshTokenRepository.save(record);

        given()
                .cookie(authSettings.getRefreshTokenCookieName(), refreshRaw)
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(200)
                .header("Set-Cookie", containsString(authSettings.getRefreshTokenCookieName() + "="))
                .header("Set-Cookie", containsString("Max-Age=0"))
                .body("message", equalTo("logged out"));
    }

    @Test
    void updateMeRejectsEmailConflict() {
        UserEntity user = createUser("first@example.com", true);
        createUser("second@example.com", true);
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "second@example.com");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .patch("/api/auth/me")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Polzovatel' s takim email uzhe sushhestvuet"));
    }

    @Test
    void changePasswordRejectsWrongCurrent() {
        UserEntity user = createUser("pwd@example.com", true);
        createIdentity(user, "local", null, passwordHasher.hash("current"));
        String token = buildToken(user.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("current_password", "wrong");
        payload.put("new_password", "newpass");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/auth/change-password")
                .then()
                .statusCode(400)
                .body("detail", equalTo("nevernyj tekushhij parol'"));
    }

    @Test
    void methodsAndDeleteFlow() {
        UserEntity user = createUser("methods@example.com", true);
        createIdentity(user, "local", null, passwordHasher.hash("secret"));
        createIdentity(user, "google", "g-1", null);
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/methods")
                .then()
                .statusCode(200)
                .body("local.active", equalTo(true))
                .body("local.can_delete", equalTo(true))
                .body("google.linked", equalTo(true))
                .body("google.provider_subject", equalTo("g-1"))
                .body("google.can_delete", equalTo(true))
                .body("yandex.linked", equalTo(false));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/auth/methods/google")
                .then()
                .statusCode(200)
                .body("google.linked", equalTo(false))
                .body("local.can_delete", equalTo(false));
    }

    private UserEntity createUser(String email, boolean active) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, "user", active, now, now);
        return userRepository.save(user);
    }

    private UserAuthIdentityEntity createIdentity(UserEntity user, String provider, String subject, String passwordHash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserAuthIdentityEntity identity =
                UserAuthIdentityEntity.create(user.getId(), provider, subject, passwordHash, now, now);
        return identityRepository.save(identity);
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
}





