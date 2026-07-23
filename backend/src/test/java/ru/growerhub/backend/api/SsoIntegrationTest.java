package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.auth.engine.AuthSettings;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SsoIntegrationTest extends IntegrationTestBase {
    private static final HttpServer PROVIDER = startProvider();
    private static final String PROVIDER_BASE = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthSettings authSettings;

    @DynamicPropertySource
    static void ssoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:growerhub_sso_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("AUTH_SSO_REDIRECT_BASE", () -> "https://growerhub.test");
        registry.add("AUTH_GOOGLE_CLIENT_ID", () -> "google-client");
        registry.add("AUTH_GOOGLE_CLIENT_SECRET", () -> "google-secret");
        registry.add("AUTH_GOOGLE_AUTH_URL", () -> PROVIDER_BASE + "/authorize");
        registry.add("AUTH_GOOGLE_TOKEN_URL", () -> PROVIDER_BASE + "/token");
        registry.add("AUTH_GOOGLE_USERINFO_URL", () -> PROVIDER_BASE + "/userinfo");
        registry.add("AUTH_YANDEX_CLIENT_ID", () -> "yandex-client");
        registry.add("AUTH_YANDEX_CLIENT_SECRET", () -> "yandex-secret");
        registry.add("AUTH_YANDEX_AUTH_URL", () -> PROVIDER_BASE + "/authorize");
        registry.add("AUTH_YANDEX_TOKEN_URL", () -> PROVIDER_BASE + "/token");
        registry.add("AUTH_YANDEX_USERINFO_URL", () -> PROVIDER_BASE + "/userinfo");
        registry.add("REFRESH_TOKEN_COOKIE_SECURE", () -> "true");
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterAll
    void stopProvider() {
        PROVIDER.stop(0);
    }

    @Test
    void newAndExistingSsoUserReceiveCookieWithoutTokenInUrl() {
        String state = requestState("yandex", "/app/onboarding/");

        Response firstCallback = callback("yandex", state);
        firstCallback.then()
                .statusCode(302)
                .header("Location", equalTo("/app/onboarding/?signup=complete"))
                .header("Location", not(containsString("access_token")))
                .header("Set-Cookie", containsString(authSettings.getRefreshTokenCookieName() + "="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .header("Set-Cookie", containsString("Secure"))
                .header("Set-Cookie", containsString("SameSite=lax"));

        String refreshCookie = firstCallback.getCookie(authSettings.getRefreshTokenCookieName());
        Response refresh = given()
                .cookie(authSettings.getRefreshTokenCookieName(), refreshCookie)
                .when()
                .post("/api/auth/refresh");
        refresh.then()
                .statusCode(200)
                .body("access_token", not(""))
                .body("token_type", equalTo("bearer"));

        given()
                .header("Authorization", "Bearer " + refresh.jsonPath().getString("access_token"))
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("sso-user@example.test"));

        callback("yandex", requestState("yandex", "/app/onboarding/"))
                .then()
                .statusCode(302)
                .header("Location", equalTo("/app/onboarding/"))
                .header("Location", not(containsString("signup=complete")))
                .header("Location", not(containsString("access_token")));

        org.junit.jupiter.api.Assertions.assertEquals(
                1L,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)
        );
    }

    @Test
    void googleSsoCreatesUserOnlyOnceAndRestoresSession() {
        Response firstCallback = callback("google", requestState("google", "/app/onboarding/"));
        firstCallback.then()
                .statusCode(302)
                .header("Location", equalTo("/app/onboarding/?signup=complete"))
                .header("Location", not(containsString("access_token")))
                .header("Set-Cookie", containsString(authSettings.getRefreshTokenCookieName() + "="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .header("Set-Cookie", containsString("Secure"));

        String refreshCookie = firstCallback.getCookie(authSettings.getRefreshTokenCookieName());
        given()
                .cookie(authSettings.getRefreshTokenCookieName(), refreshCookie)
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .body("access_token", not(""));

        callback("google", requestState("google", "/app/onboarding/"))
                .then()
                .statusCode(302)
                .header("Location", equalTo("/app/onboarding/"))
                .header("Location", not(containsString("signup=complete")))
                .header("Location", not(containsString("access_token")));

        org.junit.jupiter.api.Assertions.assertEquals(
                1L,
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)
        );
    }

    @Test
    void loginRejectsExternalRedirect() {
        given()
                .redirects().follow(false)
                .queryParam("redirect_path", "https://evil.example/app/")
                .when()
                .get("/api/auth/sso/yandex/login")
                .then()
                .statusCode(400);
    }

    private String requestState(String provider, String redirectPath) {
        Response login = given()
                .redirects().follow(false)
                .queryParam("redirect_path", redirectPath)
                .when()
                .get("/api/auth/sso/" + provider + "/login");
        login.then()
                .statusCode(302)
                .header("Location", containsString(PROVIDER_BASE + "/authorize"));
        Map<String, String> query = parseQuery(URI.create(login.header("Location")).getRawQuery());
        return query.get("state");
    }

    private Response callback(String provider, String state) {
        return given()
                .redirects().follow(false)
                .queryParam("code", "test-code")
                .queryParam("state", state)
                .when()
                .get("/api/auth/sso/" + provider + "/callback");
    }

    private static Map<String, String> parseQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(item -> item.split("=", 2))
                .collect(Collectors.toMap(
                        item -> URLDecoder.decode(item[0], StandardCharsets.UTF_8),
                        item -> URLDecoder.decode(item.length > 1 ? item[1] : "", StandardCharsets.UTF_8)
                ));
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> json(exchange, 200, "{\"access_token\":\"provider-token\"}"));
            server.createContext("/userinfo", exchange -> json(exchange, 200,
                    "{\"id\":\"subject-1\",\"default_email\":\"sso-user@example.test\","
                            + "\"is_email_verified\":true,\"sub\":\"subject-google\","
                            + "\"email\":\"google-user@example.test\",\"email_verified\":true}"));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
