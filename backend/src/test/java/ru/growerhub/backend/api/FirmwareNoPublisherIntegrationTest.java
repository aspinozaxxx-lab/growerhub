package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "MQTT_HOST=")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirmwareNoPublisherIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    private static Path firmwareDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (firmwareDir == null) {
            try {
                firmwareDir = Files.createTempDirectory("firmware-tests");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        registry.add("server.public-base-url", () -> "https://example.com");
        registry.add("firmware.binaries-dir", () -> firmwareDir.toString());
    }

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void triggerUpdateReturns503WhenPublisherMissing() throws Exception {
        String version = "1.0.0";
        Files.write(firmwareDir.resolve(version + ".bin"), "fw".getBytes(StandardCharsets.UTF_8));

        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-no-pub");
        deviceRepository.save(device);

        given()
                .contentType("application/json")
                .body(java.util.Map.of("version", version))
                .when()
                .post("/api/device/fw-no-pub/trigger-update")
                .then()
                .statusCode(503)
                .body("detail", equalTo("MQTT publisher unavailable"));
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


