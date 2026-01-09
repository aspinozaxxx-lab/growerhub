package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.device.DeviceShadowState;
import ru.growerhub.backend.device.internal.DeviceRepository;
import ru.growerhub.backend.device.internal.DeviceShadowStore;
import ru.growerhub.backend.journal.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.internal.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.internal.PlantJournalPhotoRepository;
import ru.growerhub.backend.journal.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.internal.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantGroupEntity;
import ru.growerhub.backend.plant.internal.PlantGroupRepository;
import ru.growerhub.backend.plant.internal.PlantRepository;
import ru.growerhub.backend.pump.PumpEntity;
import ru.growerhub.backend.pump.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.internal.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.internal.PumpRepository;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.internal.SensorPlantBindingRepository;
import ru.growerhub.backend.sensor.internal.SensorRepository;
import ru.growerhub.backend.sensor.SensorType;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.internal.UserRepository;

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
    private DeviceShadowStore shadowStore;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorPlantBindingRepository sensorPlantBindingRepository;

    @Autowired
    private PumpPlantBindingRepository pumpPlantBindingRepository;

    @Autowired
    private PumpRepository pumpRepository;

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
        shadowStore.clear();
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
                .body("detail", equalTo("gruppa ne naidena"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plant-groups/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("gruppa ne naidena"));
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
                .body("detail", equalTo("rastenie ne naideno"));

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
    void listPlantsIncludesSensorsAndPumps() {
        UserEntity owner = createUser("plant-state@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "State");
        DeviceEntity device = createDevice(owner, "dev-state-plant");

        SensorEntity sensor = SensorEntity.create();
        sensor.setDevice(device);
        sensor.setType(SensorType.SOIL_MOISTURE);
        sensor.setChannel(0);
        sensor.setDetected(true);
        sensorRepository.save(sensor);

        SensorPlantBindingEntity sensorBinding = SensorPlantBindingEntity.create();
        sensorBinding.setPlant(plant);
        sensorBinding.setSensor(sensor);
        sensorPlantBindingRepository.save(sensorBinding);

        PumpEntity pump = PumpEntity.create();
        pump.setDevice(device);
        pump.setChannel(0);
        pumpRepository.save(pump);

        PumpPlantBindingEntity pumpBinding = PumpPlantBindingEntity.create();
        pumpBinding.setPlant(plant);
        pumpBinding.setPump(pump);
        pumpBinding.setRateMlPerHour(2000);
        pumpPlantBindingRepository.save(pumpBinding);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].sensors", hasSize(1))
                .body("[0].sensors[0].type", equalTo("SOIL_MOISTURE"))
                .body("[0].pumps", hasSize(1));
    }

    @Test
    void listPlantsDoesNotAttachUnboundPumps() {
        UserEntity owner = createUser("plant-unbound@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plantBound = createPlant(owner, "Bound");
        PlantEntity plantFree = createPlant(owner, "Free");
        DeviceEntity device = createDevice(owner, "dev-unbound");

        PumpEntity pump = PumpEntity.create();
        pump.setDevice(device);
        pump.setChannel(0);
        pumpRepository.save(pump);

        PumpPlantBindingEntity binding = PumpPlantBindingEntity.create();
        binding.setPlant(plantBound);
        binding.setPump(pump);
        binding.setRateMlPerHour(2000);
        pumpPlantBindingRepository.save(binding);

        Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants")
                .then()
                .statusCode(200)
                .extract()
                .response();

        int boundCount = response.jsonPath()
                .getInt("find { it.id == " + plantBound.getId() + " }.pumps.size()");
        int freeCount = response.jsonPath()
                .getInt("find { it.id == " + plantFree.getId() + " }.pumps.size()");

        Assertions.assertEquals(1, boundCount);
        Assertions.assertEquals(0, freeCount);
    }

    @Test
    void listPlantsShowsRunningPumpForAllBoundPlants() {
        UserEntity owner = createUser("plant-running@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plantA = createPlant(owner, "A");
        PlantEntity plantB = createPlant(owner, "B");
        DeviceEntity device = createDevice(owner, "dev-running");

        PumpEntity pump = PumpEntity.create();
        pump.setDevice(device);
        pump.setChannel(0);
        pumpRepository.save(pump);

        PumpPlantBindingEntity bindingA = PumpPlantBindingEntity.create();
        bindingA.setPlant(plantA);
        bindingA.setPump(pump);
        bindingA.setRateMlPerHour(2000);
        pumpPlantBindingRepository.save(bindingA);

        PumpPlantBindingEntity bindingB = PumpPlantBindingEntity.create();
        bindingB.setPlant(plantB);
        bindingB.setPump(pump);
        bindingB.setRateMlPerHour(2000);
        pumpPlantBindingRepository.save(bindingB);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceShadowState.ManualWateringState manual = new DeviceShadowState.ManualWateringState(
                "running",
                120,
                now,
                120,
                "corr-plant"
        );
        DeviceShadowState state = new DeviceShadowState(manual, null, null, null, null, null, null, null, null, null);
        shadowStore.updateFromState(device.getDeviceId(), state, now);

        Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants")
                .then()
                .statusCode(200)
                .extract()
                .response();

        boolean runningA = response.jsonPath()
                .getBoolean("find { it.id == " + plantA.getId() + " }.pumps.find { it.id == " + pump.getId() + " }.is_running");
        boolean runningB = response.jsonPath()
                .getBoolean("find { it.id == " + plantB.getId() + " }.pumps.find { it.id == " + pump.getId() + " }.is_running");

        Assertions.assertTrue(runningA);
        Assertions.assertTrue(runningB);
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
                .body("detail", equalTo("rastenie ne naideno"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", "Nope"))
                .when()
                .patch("/api/plants/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne naideno"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/99999")
                .then()
                .statusCode(404)
                .body("detail", equalTo("rastenie ne naideno"));
    }

    @Test
    void harvestUpdatesPlantAndCreatesJournalEntry() {
        UserEntity owner = createUser("harvest-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "Harvested");

        LocalDateTime harvestedAt = LocalDateTime.of(2026, 1, 9, 10, 15, 30);
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "first harvest");
        payload.put("harvested_at", harvestedAt.toString());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/api/plants/" + plant.getId() + "/harvest")
                .then()
                .statusCode(200)
                .body("message", equalTo("plant harvested"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants")
                .then()
                .statusCode(200)
                .body("find { it.id == " + plant.getId() + " }.harvested_at", notNullValue());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(200)
                .body("find { it.type == 'harvest' }.text", equalTo("first harvest"))
                .body("find { it.type == 'harvest' }.event_at", equalTo(harvestedAt.toString()));
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
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/plants/" + plant.getId() + "/journal/" + entryId)
                .then()
                .statusCode(404)
                .body("detail", equalTo("zapis' ne naidena"));
    }

    @Test
    void journalListReturnsTextPayload() {
        UserEntity owner = createUser("journal-text@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "JournalText");

        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlant(plant);
        entry.setUser(owner);
        entry.setType("note");
        entry.setText("obem_vody=1.50l; dlitelnost=675s; ph=6.3");
        entry.setEventAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(entry);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].text", equalTo("obem_vody=1.50l; dlitelnost=675s; ph=6.3"));
    }

    @Test
    void journalListReturnsWateringDetailsText() {
        UserEntity owner = createUser("journal-details@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "JournalDetails");

        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlant(plant);
        entry.setUser(owner);
        entry.setType("watering");
        entry.setText("details");
        entry.setEventAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(entry);

        PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
        details.setJournalEntry(entry);
        details.setWaterVolumeL(1.0);
        details.setDurationS(675);
        details.setPh(6.3);
        details.setFertilizersPerLiter("G8M12B16 kapel");
        plantJournalWateringDetailsRepository.save(details);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plant.getId() + "/journal")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].watering_details.fertilizers_per_liter", equalTo("G8M12B16 kapel"));
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
        Assertions.assertTrue(body.contains("Nazvanie: ExportPlant"));
        Assertions.assertTrue(body.contains("Data posadki: 2025-01-01"));
        Assertions.assertTrue(body.contains("1,5"));
        Assertions.assertTrue(body.contains("udobreniya"));
        Assertions.assertTrue(body.contains("Poliv"));

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
                .body("detail", equalTo("foto ne naideno ili nedostupno"));

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
                .body("detail", equalTo("foto ne naideno ili nedostupno"));
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
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }
}


