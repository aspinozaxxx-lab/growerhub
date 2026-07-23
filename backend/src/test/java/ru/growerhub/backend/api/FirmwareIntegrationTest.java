package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceCredential;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdOta;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(FirmwareIntegrationTest.PublisherConfig.class)
class FirmwareIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceFacade deviceFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestPublisher testPublisher;

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
    void setUp() throws Exception {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        testPublisher.reset();
        if (Files.exists(firmwareDir)) {
            try (var stream = Files.newDirectoryStream(firmwareDir)) {
                for (Path item : stream) {
                    Files.deleteIfExists(item);
                }
            }
        }
    }

    @Test
    void checkFirmwareReturnsUpdateWhenAvailable() {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-check-1");
        device.setUpdateAvailable(true);
        device.setLatestVersion("2.0.1");
        deviceRepository.save(device);
        String deviceToken = createDeviceCredential(device);

        given()
                .header("Authorization", "Device " + deviceToken)
                .when()
                .get("/api/device/fw-check-1/firmware")
                .then()
                .statusCode(200)
                .body("$", aMapWithSize(3))
                .body("update_available", equalTo(true))
                .body("latest_version", equalTo("2.0.1"))
                .body("firmware_url", equalTo("http://192.168.0.11/firmware/2.0.1.bin"));
    }

    @Test
    void checkFirmwareRejectsUnknownDevice() {
        given()
                .when()
                .get("/api/device/fw-missing/firmware")
                .then()
                .statusCode(401)
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void uploadTriggerAndServeFirmware() throws Exception {
        String version = "9.9.9";
        byte[] data = "firmware-bytes".getBytes(StandardCharsets.UTF_8);
        String adminToken = createAdminToken("firmware-flow-admin@example.com");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .multiPart("file", "firmware.bin", data, "application/octet-stream")
                .multiPart("version", version)
                .when()
                .post("/api/upload-firmware")
                .then()
                .statusCode(201)
                .body("$", aMapWithSize(3))
                .body("result", equalTo("created"))
                .body("version", equalTo(version));

        Path stored = firmwareDir.resolve(version + ".bin");
        Assertions.assertTrue(Files.exists(stored));
        Assertions.assertArrayEquals(data, Files.readAllBytes(stored));

        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-dev-1");
        deviceRepository.save(device);

        String expectedSha = sha256Hex(data);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("version", version))
                .when()
                .post("/api/device/fw-dev-1/trigger-update")
                .then()
                .statusCode(202)
                .body("$", aMapWithSize(4))
                .body("result", equalTo("accepted"))
                .body("version", equalTo(version))
                .body("url", equalTo("https://example.com/firmware/" + version + ".bin"))
                .body("sha256", equalTo(expectedSha));

        Assertions.assertEquals(1, testPublisher.getPublished().size());
        PublishedCommand published = testPublisher.getPublished().get(0);
        Assertions.assertEquals("fw-dev-1", published.deviceId());
        Assertions.assertTrue(published.cmd() instanceof CmdOta);
        CmdOta cmd = (CmdOta) published.cmd();
        Assertions.assertEquals("ota", cmd.type());
        Assertions.assertEquals(expectedSha, cmd.sha256());

        DeviceEntity storedDevice = deviceRepository.findByDeviceId("fw-dev-1").orElse(null);
        Assertions.assertNotNull(storedDevice);
        Assertions.assertEquals(version, storedDevice.getLatestVersion());
        Assertions.assertEquals("https://example.com/firmware/" + version + ".bin", storedDevice.getFirmwareUrl());
        Assertions.assertEquals(true, storedDevice.getUpdateAvailable());

        Response download = given()
                .when()
                .get("/firmware/" + version + ".bin")
                .then()
                .statusCode(200)
                .contentType("application/octet-stream")
                .extract()
                .response();
        Assertions.assertArrayEquals(data, download.asByteArray());
    }

    @Test
    void uploadEmptyFileReturns500() {
        String adminToken = createAdminToken("firmware-empty-admin@example.com");
        given()
                .header("Authorization", "Bearer " + adminToken)
                .multiPart("file", "empty.bin", new byte[0], "application/octet-stream")
                .multiPart("version", "1.0.0")
                .when()
                .post("/api/upload-firmware")
                .then()
                .statusCode(500)
                .body("detail", equalTo("empty file"));
    }

    @Test
    void uploadMissingFileReturns422() {
        String adminToken = createAdminToken("firmware-missing-file-admin@example.com");
        given()
                .header("Authorization", "Bearer " + adminToken)
                .multiPart("version", "1.0.1")
                .when()
                .post("/api/upload-firmware")
                .then()
                .statusCode(422);
    }

    @Test
    void uploadMissingVersionReturns422() {
        String adminToken = createAdminToken("firmware-missing-version-admin@example.com");
        given()
                .header("Authorization", "Bearer " + adminToken)
                .multiPart("file", "firmware.bin", "data".getBytes(StandardCharsets.UTF_8), "application/octet-stream")
                .when()
                .post("/api/upload-firmware")
                .then()
                .statusCode(422);
    }

    @Test
    void triggerUpdateMissingDeviceReturns404() {
        String adminToken = createAdminToken("firmware-missing-device-admin@example.com");
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("version", "1.2.3"))
                .when()
                .post("/api/device/fw-missing/trigger-update")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Ustrojstvo ne naideno"));
    }

    @Test
    void triggerUpdateMissingFileReturns404() {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-missing-file");
        deviceRepository.save(device);
        String adminToken = createAdminToken("firmware-trigger-file-admin@example.com");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("version", "2.0.0"))
                .when()
                .post("/api/device/fw-missing-file/trigger-update")
                .then()
                .statusCode(404)
                .body("detail", equalTo("firmware not found"));
    }

    @Test
    void triggerUpdateValidationMissingVersionReturns422() {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-validate");
        deviceRepository.save(device);
        String adminToken = createAdminToken("firmware-validate-admin@example.com");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of())
                .when()
                .post("/api/device/fw-validate/trigger-update")
                .then()
                .statusCode(422)
                .body("detail[0].loc[1]", equalTo("__root__"))
                .body("detail[0].msg", equalTo("version required"))
                .body("detail[0].type", equalTo("value_error"));
    }

    @Test
    void triggerUpdatePublishFailureReturns503() throws Exception {
        String version = "3.0.0";
        byte[] data = "firmware".getBytes(StandardCharsets.UTF_8);
        Files.write(firmwareDir.resolve(version + ".bin"), data);

        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("fw-fail");
        deviceRepository.save(device);
        String adminToken = createAdminToken("firmware-publish-admin@example.com");

        testPublisher.failNext();

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("version", version))
                .when()
                .post("/api/device/fw-fail/trigger-update")
                .then()
                .statusCode(503)
                .body("detail", equalTo("mqtt publish failed"));
    }

    @Test
    void listFirmwareVersionsSortedAndMetadata() throws Exception {
        String adminToken = createAdminToken("firmware-list-admin@example.com");
        Path newer = firmwareDir.resolve("2.3.4.bin");
        Path older = firmwareDir.resolve("1.0.0.bin");
        byte[] newBytes = "newer".getBytes(StandardCharsets.UTF_8);
        byte[] oldBytes = "older".getBytes(StandardCharsets.UTF_8);
        Files.write(newer, newBytes);
        Files.write(older, oldBytes);
        Files.setLastModifiedTime(newer, FileTime.from(Instant.now()));
        Files.setLastModifiedTime(older, FileTime.from(Instant.now().minusSeconds(86400)));

        Response response = given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/firmware/versions")
                .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .extract()
                .response();

        List<Map<String, Object>> payload = response.jsonPath().getList("$");
        Assertions.assertEquals("2.3.4", payload.get(0).get("version"));
        Assertions.assertEquals("1.0.0", payload.get(1).get("version"));
        Assertions.assertEquals(sha256Hex(newBytes), payload.get(0).get("sha256"));
        Assertions.assertTrue(payload.get(0).get("mtime").toString().endsWith("Z"));
        Assertions.assertEquals(4, payload.get(0).size());
    }

    @Test
    void listFirmwareVersionsEmpty() {
        String adminToken = createAdminToken("firmware-list-empty-admin@example.com");
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/firmware/versions")
                .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .body("size()", equalTo(0));
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }

    private String createDeviceCredential(DeviceEntity device) {
        DeviceCredential credential = deviceFacade.rotateDeviceCredential(device.getId(), null, true);
        return credential.token();
    }

    private String createAdminToken(String email) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity admin = userRepository.save(UserEntity.create(email, null, "admin", true, now, now));
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", admin.getId());
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

    @TestConfiguration
    static class PublisherConfig {

        @Bean
        TestPublisher testPublisher() {
            return new TestPublisher();
        }
    }

    static class TestPublisher implements MqttPublisher {
        private final List<PublishedCommand> published = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final java.util.concurrent.atomic.AtomicBoolean failNext = new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public void publishCmd(String deviceId, Object cmd) {
            if (failNext.compareAndSet(true, false)) {
                throw new RuntimeException("publisher failure");
            }
            published.add(new PublishedCommand(deviceId, cmd));
        }

        void failNext() {
            failNext.set(true);
        }

        void reset() {
            published.clear();
            failNext.set(false);
        }

        List<PublishedCommand> getPublished() {
            return published;
        }
    }

    private record PublishedCommand(String deviceId, Object cmd) {
    }
}

