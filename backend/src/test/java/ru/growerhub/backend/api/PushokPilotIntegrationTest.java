package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:pushok_pilot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class PushokPilotIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    UserEntity owner;
    UserEntity other;
    UserEntity admin;

    @BeforeEach
    void setup() {
        users.deleteAll();
        owner = create("pilot@example.test", "user");
        other = create("other@example.test", "user");
        admin = create("admin@example.test", "admin");
    }

    @Test
    void persistsOneRequestAndDoesNotCreateOrChangeEquipment() {
        Map<String, Object> input = Map.of("contact_method", "TELEGRAM", "contact", " @pilot_user ",
                "equipment", " POK100, датчик почвы ", "user_id", other.getId());
        long coordinators = jdbc.queryForObject("select count(*) from zigbee_coordinators", Long.class);
        long devices = jdbc.queryForObject("select count(*) from devices", Long.class);
        String date = request(owner).body(input).put("/api/users/me/pushok-pilot").then().statusCode(200)
                .body("request.contact", equalTo("@pilot_user"))
                .body("request.equipment", equalTo("POK100, датчик почвы"))
                .extract().path("request.requested_at");
        request(owner).body(input).put("/api/users/me/pushok-pilot").then().statusCode(200)
                .body("request.requested_at", equalTo(date));
        request(owner).get("/api/users/me/pushok-pilot").then().statusCode(200)
                .body("request.contact", equalTo("@pilot_user"));
        request(other).get("/api/users/me/pushok-pilot").then().statusCode(200).body("request", nullValue());
        request(admin).get("/api/admin/pushok-pilots").then().statusCode(200)
                .body("size()", equalTo(1)).body("[0].user_id", equalTo(owner.getId()))
                .body("[0].request.contact", equalTo("@pilot_user"));
        assertThat(jdbc.queryForObject("select count(*) from zigbee_coordinators", Long.class)).isEqualTo(coordinators);
        assertThat(jdbc.queryForObject("select count(*) from devices", Long.class)).isEqualTo(devices);
        assertThat(users.findById(owner.getId()).orElseThrow().getOnboardingCompletedAt()).isNull();
    }

    @Test
    void requiresAuthenticationAndProtectsAdministrativeOperations() {
        request(null).get("/api/users/me/pushok-pilot").then().statusCode(401);
        request(null).body(Map.of("contact_method", "EMAIL", "contact", "a@example.test"))
                .put("/api/users/me/pushok-pilot").then().statusCode(401);
        request(owner).get("/api/admin/pushok-pilots").then().statusCode(403);
        request(owner).post("/api/admin/pushok-pilots/" + other.getId() + "/contacted").then().statusCode(403);
        request(owner).get("/api/users/" + other.getId()).then().statusCode(403);
    }

    @Test
    void rejectsInvalidContactsWithoutSavingARequest() {
        for (var input : java.util.List.of(
                Map.of("contact_method", "EMAIL", "contact", "not-an-email"),
                Map.of("contact_method", "TELEGRAM", "contact", "https://other.example/pilot"),
                Map.of("contact_method", "OTHER", "contact", "   "),
                Map.of("contact_method", "OTHER", "contact", "x".repeat(255)),
                Map.of("contact_method", "OTHER", "contact", "hello\nthere"),
                Map.of("contact_method", "EMAIL", "contact", "a@example.test", "equipment", "x".repeat(501))
        )) request(owner).body(input).put("/api/users/me/pushok-pilot").then().statusCode(400);
        request(owner).get("/api/users/me/pushok-pilot").then().body("request", nullValue());
        assertThat(users.findByPilotRequestedAtIsNotNull()).isEmpty();
    }

    @Test
    void contactUpdateReopensRequestAndWithdrawalRemovesPersonalDetails() {
        var first = Map.of("contact_method", "EMAIL", "contact", "a@example.test");
        request(owner).body(first).put("/api/users/me/pushok-pilot").then().statusCode(200);
        String marked = request(admin).post("/api/admin/pushok-pilots/" + owner.getId() + "/contacted")
                .then().statusCode(200).extract().path("request.contacted_at");
        request(owner).body(first).put("/api/users/me/pushok-pilot").then().statusCode(200)
                .body("request.contacted_at", equalTo(marked));
        request(owner).body(Map.of("contact_method", "OTHER", "contact", "Позвоните: +7 900 000 00 00"))
                .put("/api/users/me/pushok-pilot").then().statusCode(200).body("request.contacted_at", nullValue());
        request(other).delete("/api/users/me/pushok-pilot").then().statusCode(204);
        assertThat(users.findById(owner.getId()).orElseThrow().getPilotContact()).isNotNull();
        request(owner).delete("/api/users/me/pushok-pilot").then().statusCode(204);
        var stored = users.findById(owner.getId()).orElseThrow();
        assertThat(stored.getPilotContact()).isNull();
        assertThat(stored.getPilotContactMethod()).isNull();
        assertThat(stored.getPilotEquipment()).isNull();
        assertThat(stored.getPilotRequestedAt()).isNull();
        request(admin).get("/api/admin/pushok-pilots").then().body("size()", equalTo(0));
    }

    private UserEntity create(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return users.save(UserEntity.create(email, "Pilot QA", role, true, now, now));
    }

    private RequestSpecification request(UserEntity user) {
        var request = given().baseUri("http://localhost").port(port).contentType("application/json");
        if (user == null) return request;
        String token = Jwts.builder().claim("user_id", user.getId())
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        return request.header("Authorization", "Bearer " + token);
    }
}
