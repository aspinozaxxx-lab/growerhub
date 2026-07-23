package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.automation.jpa.AutomationBoxEntity;
import ru.growerhub.backend.automation.jpa.AutomationBoxRepository;
import ru.growerhub.backend.automation.jpa.AutomationRoomEntity;
import ru.growerhub.backend.automation.jpa.AutomationRoomRepository;
import ru.growerhub.backend.automation.jpa.AutomationScenarioConfigEntity;
import ru.growerhub.backend.automation.jpa.AutomationScenarioConfigRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST="}
)
class ProductAnalyticsIntegrationTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZigbeeCoordinatorRepository coordinatorRepository;

    @Autowired
    private AutomationRoomRepository roomRepository;

    @Autowired
    private AutomationBoxRepository boxRepository;

    @Autowired
    private AutomationScenarioConfigRepository scenarioRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void adminReceivesAnonymousFunnelWithoutPersonalData() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity admin = createUser("analytics-admin@example.com", "admin", now);
        UserEntity activated = createUser("activated@example.com", "user", now);
        UserEntity started = createUser("started@example.com", "user", now);

        ZigbeeCoordinatorEntity online = createCoordinator(activated.getId(), "online", now);
        online.setConnectedAt(now.minusHours(2));
        online.setFirstDeviceSeenAt(now.minusHours(1));
        online.setLastSeenAt(now.minusMinutes(10));
        coordinatorRepository.save(online);
        coordinatorRepository.save(createCoordinator(started.getId(), "started", now));

        AutomationRoomEntity zone = roomRepository.save(
                AutomationRoomEntity.create(activated.getId(), "Synthetic zone", now)
        );
        AutomationBoxEntity section = boxRepository.save(
                AutomationBoxEntity.create(zone, "Synthetic section", now)
        );
        AutomationScenarioConfigEntity scenario = AutomationScenarioConfigEntity.create(
                AutomationData.SCOPE_BOX,
                section.getId(),
                AutomationData.SCENARIO_LIGHT_SCHEDULE,
                now
        );
        scenario.setEnabled(true);
        scenarioRepository.save(scenario);

        String response = given()
                .header("Authorization", "Bearer " + buildToken(admin.getId()))
                .when()
                .get("/api/admin/product-analytics")
                .then()
                .statusCode(200)
                .body("registrations", equalTo(2))
                .body("users_with_coordinator", equalTo(2))
                .body("users_with_connected_coordinator", equalTo(1))
                .body("users_with_first_device", equalTo(1))
                .body("users_with_zone", equalTo(1))
                .body("users_with_automation", equalTo(1))
                .body("coordinators_created", equalTo(2))
                .body("coordinators_connected", equalTo(1))
                .body("zones_created", equalTo(1))
                .body("automations_enabled", equalTo(1))
                .body("active_coordinators_1d", equalTo(1))
                .body("active_coordinators_7d", equalTo(1))
                .body("active_coordinators_28d", equalTo(1))
                .extract()
                .asString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain("analytics-admin@example.com")
                .doesNotContain("activated@example.com")
                .doesNotContain("started@example.com");
    }

    @Test
    void endpointRequiresAdmin() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = createUser("regular@example.com", "user", now);

        given().when().get("/api/admin/product-analytics").then().statusCode(401);
        given()
                .header("Authorization", "Bearer " + buildToken(user.getId()))
                .when()
                .get("/api/admin/product-analytics")
                .then()
                .statusCode(403);
    }

    private ZigbeeCoordinatorEntity createCoordinator(Integer userId, String name, LocalDateTime now) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "z2m_" + key;
        return ZigbeeCoordinatorEntity.create(
                UUID.randomUUID(),
                userId,
                name,
                username,
                "gh/z2m/" + username,
                now
        );
    }

    private UserEntity createUser(String email, String role, LocalDateTime now) {
        return userRepository.save(UserEntity.create(email, null, role, true, now, now));
    }

    private String buildToken(int userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
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
}
