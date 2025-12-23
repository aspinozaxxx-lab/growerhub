
package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantEntity;
import ru.growerhub.backend.db.PlantGroupEntity;
import ru.growerhub.backend.db.PlantGroupRepository;
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalPhotoEntity;
import ru.growerhub.backend.db.PlantJournalPhotoRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.db.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.db.PlantRepository;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlantsIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlantGroupRepository plantGroupRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlantDeviceRepository plantDeviceRepository;

    @Autowired
    private PlantJournalEntryRepository plantJournalEntryRepository;

    @Autowired
    private PlantJournalPhotoRepository plantJournalPhotoRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void plantGroupsCrudAndValidation() {
        UserEntity owner = createUser("group-owner@example.com", "user");
        String token = buildToken(owner.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "My Group"))
                .when()
                .post("/api/plant-groups")
                .then()
                .statusCode(200)
                .body("name", equalTo("My Group"))
                .body("user_id", equalTo(owner.getId()));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plant-groups")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1));

        PlantGroupEntity stored = plantGroupRepository.findAll().get(0);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Updated"))
                .when()
                .patch("/api/plant-groups/" + stored.getId())
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plant-groups/" + stored.getId())
                .then()
                .statusCode(200)
                .body("message", equalTo("group deleted"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(new HashMap<>())
                .when()
                .post("/api/plant-groups")
                .then()
                .statusCode(422);
    }

    @Test
    void plantGroupsNotFoundDetails() {
        UserEntity owner = createUser("group-missing@example.com", "user");
        String token = buildToken(owner.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Missing"))
                .when()
                .patch("/api/plant-groups/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("gruppa ne najdena"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plant-groups/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("gruppa ne najdena"));
    }

    @Test
    void plantGroupsRequireAuth() {
        given()
                .when()
                .get("/api/plant-groups")
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));
    }

    @Test
    void plantCrudAndPatchAllowsNullGroup() {
        UserEntity owner = createUser("plant-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantGroupEntity group = createGroup(owner, "Group A");

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Basil");
        payload.put("plant_group_id", group.getId());
        payload.put("plant_type", "flowering");
        payload.put("strain", "Mint");
        payload.put("growth_stage", "seedling");

        Response create = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/plants")
                .then()
                .statusCode(200)
                .body("user_id", equalTo(owner.getId()))
                .extract()
                .response();

        Integer plantId = create.jsonPath().getInt("id");

        Map<String, Object> clearGroup = new HashMap<>();
        clearGroup.put("plant_group_id", null);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(clearGroup)
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("plant_group", nullValue());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("strain", "New", "growth_stage", "flowering"))
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("strain", equalTo("New"))
                .body("growth_stage", equalTo("flowering"));

        UserEntity other = createUser("plant-other@example.com", "user");
        String otherToken = buildToken(other.getId());

        given()
                .header("Authorization", "Bearer " + otherToken)
                .when()
                .get("/api/plants/" + plantId)
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne najdeno"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("message", equalTo("plant deleted"));
    }

    @Test
    void plantValidationReturns422() {
        UserEntity owner = createUser("plant-validate@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "Validate");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(new HashMap<>())
                .when()
                .post("/api/plants")
                .then()
                .statusCode(422);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("planted_at", "not-a-date"))
                .when()
                .patch("/api/plants/" + plant.getId())
                .then()
                .statusCode(422);
    }

    @Test
    void plantNotFoundDetails() {
        UserEntity owner = createUser("plant-missing@example.com", "user");
        String token = buildToken(owner.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne najdeno"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Nope"))
                .when()
                .patch("/api/plants/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne najdeno"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne najdeno"));
    }

    @Test
    void attachAndDetachDevices() {
        UserEntity owner = createUser("device-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "Rose");
        DeviceEntity device = createDevice(owner, "dev-attach");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/plants/" + plant.getId() + "/devices/" + device.getId())
                .then()
                .statusCode(200)
                .body("devices", hasSize(1));

        List<PlantDeviceEntity> links = plantDeviceRepository.findAllByPlant_Id(plant.getId());
        Assertions.assertEquals(1, links.size());

        Response detach = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plant.getId() + "/devices/" + device.getId())
                .then()
                .statusCode(204)
                .extract()
                .response();
        Assertions.assertEquals("", detach.asString());

        UserEntity other = createUser("device-other@example.com", "user");
        DeviceEntity otherDevice = createDevice(other, "dev-other");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/plants/" + plant.getId() + "/devices/" + otherDevice.getId())
                .then()
                .statusCode(404)
                .body("detail", equalTo("ustrojstvo ne najdeno"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plant.getId() + "/devices/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("ustrojstvo ne najdeno"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/plants/99999/devices/" + device.getId())
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne najdeno"));
    }

    @Test
    void journalCrudAndValidation() {
        UserEntity owner = createUser("journal-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "Mint");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(new HashMap<>())
                .when()
                .post("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(422);

        Response created = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("type", "note", "text", "first"))
                .when()
                .post("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(200)
                .extract()
                .response();

        Integer entryId = created.jsonPath().getInt("id");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("text", "updated", "type", "other"))
                .when()
                .patch("/api/plants/" + plant.getId() + "/journal/" + entryId)
                .then()
                .statusCode(200)
                .body("text", equalTo("updated"))
                .body("type", equalTo("other"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plant.getId() + "/journal/" + entryId)
                .then()
                .statusCode(200)
                .body("message", equalTo("entry deleted"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plant.getId() + "/journal/" + entryId)
                .then()
                .statusCode(404)
                .body("detail", equalTo("zapis' ne najdena"));
    }

    @Test
    void journalExportMarkdown() {
        UserEntity owner = createUser("export-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "ExportPlant");
        plant.setPlantedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        plantRepository.save(plant);

        PlantJournalEntryEntity watering = PlantJournalEntryEntity.create();
        watering.setPlant(plant);
        watering.setUser(owner);
        watering.setType("watering");
        watering.setText("");
        watering.setEventAt(LocalDateTime.of(2025, 1, 2, 8, 30));
        watering.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        watering.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(watering);

        PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
        details.setJournalEntry(watering);
        details.setWaterVolumeL(1.5);
        details.setDurationS(120);
        details.setFertilizersPerLiter("G8 M12 B16");
        plantJournalWateringDetailsRepository.save(details);

        PlantJournalEntryEntity feeding = PlantJournalEntryEntity.create();
        feeding.setPlant(plant);
        feeding.setUser(owner);
        feeding.setType("feeding");
        feeding.setText("podrezka listev");
        feeding.setEventAt(LocalDateTime.of(2025, 1, 2, 12, 0));
        feeding.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        feeding.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(feeding);

        Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal/export?format=md")
                .then()
                .statusCode(200)
                .header("Content-Disposition", equalTo("attachment; filename=\"plant_journal_" + plant.getId() + ".md\""))
                .extract()
                .response();

        String contentType = response.getHeader("Content-Type");
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase().replace(" ", "");
        Assertions.assertEquals("text/markdown;charset=utf-8", normalizedContentType);

        String body = response.asString();
        Assertions.assertTrue(body.startsWith("# "));
        Assertions.assertFalse(body.trim().startsWith("{"));
        Assertions.assertTrue(body.contains("Название: ExportPlant"));
        Assertions.assertTrue(body.contains("Дата посадки: 2025-01-01"));
        Assertions.assertTrue(body.contains("1,5"));
        Assertions.assertTrue(body.contains("udobreniya"));
        Assertions.assertTrue(body.contains("Полив"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal/export?format=txt")
                .then()
                .statusCode(400)
                .body("detail", equalTo("podderzhivaetsya tolko format=md"));
    }

    @Test
    void journalPhotoDownloadRequiresOwner() {
        UserEntity owner = createUser("photo-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "PhotoPlant");

        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlant(plant);
        entry.setUser(owner);
        entry.setType("photo");
        entry.setText("with data");
        entry.setEventAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(entry);

        PlantJournalPhotoEntity photo = PlantJournalPhotoEntity.create();
        photo.setJournalEntry(entry);
        photo.setUrl(null);
        photo.setData("hello-world".getBytes(StandardCharsets.UTF_8));
        photo.setContentType("text/plain");
        plantJournalPhotoRepository.save(photo);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/journal/photos/" + photo.getId())
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/plain"));

        given()
                .when()
                .get("/api/journal/photos/" + photo.getId())
                .then()
                .statusCode(401)
                .header("WWW-Authenticate", "Bearer")
                .body("detail", equalTo("Not authenticated"));

        UserEntity other = createUser("photo-other@example.com", "user");
        String otherToken = buildToken(other.getId());

        given()
                .header("Authorization", "Bearer " + otherToken)
                .when()
                .get("/api/journal/photos/" + photo.getId())
                .then()
                .statusCode(404)
                .body("detail", equalTo("foto ne najdeno ili nedostupno"));

        PlantJournalPhotoEntity empty = PlantJournalPhotoEntity.create();
        empty.setJournalEntry(entry);
        empty.setUrl(null);
        plantJournalPhotoRepository.save(empty);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/journal/photos/" + empty.getId())
                .then()
                .statusCode(404)
                .body("detail", equalTo("binarnye dannye dlya etogo foto otsutstvuyut"));
    }

    @Test
    void journalPhotoAccessDeniedForOtherUserReturnsExactDetail() {
        UserEntity owner = createUser("photo-owner-a@example.com", "user");
        UserEntity other = createUser("photo-owner-b@example.com", "user");
        String otherToken = buildToken(other.getId());
        PlantEntity plant = createPlant(owner, "PhotoAccess");

        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlant(plant);
        entry.setUser(owner);
        entry.setType("photo");
        entry.setText("private");
        entry.setEventAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(entry);

        PlantJournalPhotoEntity photo = PlantJournalPhotoEntity.create();
        photo.setJournalEntry(entry);
        photo.setUrl(null);
        photo.setData("secret".getBytes(StandardCharsets.UTF_8));
        photo.setContentType("text/plain");
        plantJournalPhotoRepository.save(photo);

        given()
                .header("Authorization", "Bearer " + otherToken)
                .when()
                .get("/api/journal/photos/" + photo.getId())
                .then()
                .statusCode(404)
                .body("detail", equalTo("foto ne najdeno ili nedostupno"));
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, role, true, now, now);
        return userRepository.save(user);
    }

    private PlantGroupEntity createGroup(UserEntity owner, String name) {
        PlantGroupEntity group = PlantGroupEntity.create();
        group.setName(name);
        group.setUser(owner);
        group.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        group.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantGroupRepository.save(group);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setName(name);
        plant.setUser(owner);
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        plant.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plant.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantRepository.save(plant);
    }

    private DeviceEntity createDevice(UserEntity owner, String deviceId) {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId(deviceId);
        device.setName("Device " + deviceId);
        device.setUser(owner);
        device.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        return deviceRepository.save(device);
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
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_devices");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM plant_groups");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM sensor_data");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}
