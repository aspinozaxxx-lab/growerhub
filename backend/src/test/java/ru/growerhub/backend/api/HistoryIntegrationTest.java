package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.db.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.db.PlantRepository;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantDeviceRepository plantDeviceRepository;

    @Autowired
    private PlantJournalEntryRepository plantJournalEntryRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
    }

    @Test
    void sensorHistoryFiltersByHours() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SensorDataEntity fresh = SensorDataEntity.create();
        fresh.setDeviceId("hist-device-1");
        fresh.setTimestamp(now.minusHours(2));
        fresh.setSoilMoisture(31.5);
        fresh.setAirTemperature(24.2);
        fresh.setAirHumidity(52.1);
        sensorDataRepository.save(fresh);

        SensorDataEntity stale = SensorDataEntity.create();
        stale.setDeviceId("hist-device-1");
        stale.setTimestamp(now.minusDays(2));
        stale.setSoilMoisture(28.0);
        stale.setAirTemperature(19.4);
        stale.setAirHumidity(40.0);
        sensorDataRepository.save(stale);

        SensorDataEntity other = SensorDataEntity.create();
        other.setDeviceId("other-device");
        other.setTimestamp(now.minusHours(1));
        other.setSoilMoisture(44.0);
        other.setAirTemperature(21.0);
        other.setAirHumidity(48.0);
        sensorDataRepository.save(other);

        given()
                .queryParam("hours", 24)
                .when()
                .get("/api/device/hist-device-1/sensor-history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].soil_moisture", equalTo(31.5f))
                .body("[0].air_temperature", equalTo(24.2f))
                .body("[0].air_humidity", equalTo(52.1f));
    }

    @Test
    void sensorDataHasRelayColumns() {
        Integer light = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SENSOR_DATA' AND column_name = 'LIGHT_RELAY_ON'",
                Integer.class
        );
        Integer pump = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SENSOR_DATA' AND column_name = 'PUMP_RELAY_ON'",
                Integer.class
        );
        Integer soil1 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SENSOR_DATA' AND column_name = 'SOIL_MOISTURE_1'",
                Integer.class
        );
        Integer soil2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'SENSOR_DATA' AND column_name = 'SOIL_MOISTURE_2'",
                Integer.class
        );

        Assertions.assertEquals(1, light);
        Assertions.assertEquals(1, pump);
        Assertions.assertEquals(1, soil1);
        Assertions.assertEquals(1, soil2);
    }

    @Test
    void wateringLogsFiltersByDays() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("hist-device-2");
        deviceRepository.save(device);

        PlantEntity plant = PlantEntity.create();
        plant.setName("History Plant");
        plant.setPlantedAt(now.minusDays(5));
        plantRepository.save(plant);

        PlantDeviceEntity link = PlantDeviceEntity.create();
        link.setPlant(plant);
        link.setDevice(device);
        plantDeviceRepository.save(link);

        PlantJournalEntryEntity recent = PlantJournalEntryEntity.create();
        recent.setPlant(plant);
        recent.setType("watering");
        recent.setEventAt(now.minusDays(1));
        plantJournalEntryRepository.save(recent);

        PlantJournalEntryEntity old = PlantJournalEntryEntity.create();
        old.setPlant(plant);
        old.setType("watering");
        old.setEventAt(now.minusDays(10));
        plantJournalEntryRepository.save(old);

        PlantJournalWateringDetailsEntity recentDetails = PlantJournalWateringDetailsEntity.create();
        recentDetails.setJournalEntry(recent);
        recentDetails.setWaterVolumeL(0.5);
        recentDetails.setDurationS(120);
        recentDetails.setPh(6.2);
        recentDetails.setFertilizersPerLiter("NPK 10-10-10");
        plantJournalWateringDetailsRepository.save(recentDetails);

        PlantJournalWateringDetailsEntity oldDetails = PlantJournalWateringDetailsEntity.create();
        oldDetails.setJournalEntry(old);
        oldDetails.setWaterVolumeL(0.3);
        oldDetails.setDurationS(90);
        oldDetails.setPh(null);
        oldDetails.setFertilizersPerLiter(null);
        plantJournalWateringDetailsRepository.save(oldDetails);

        given()
                .queryParam("days", 7)
                .when()
                .get("/api/device/hist-device-2/watering-logs")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].duration", equalTo(120))
                .body("[0].water_used", equalTo(0.5f))
                .body("[0].ph", equalTo(6.2f))
                .body("[0].fertilizers_per_liter", equalTo("NPK 10-10-10"));
    }

    @Test
    void historyMissingDeviceReturnsEmptyList() {
        given()
                .queryParam("hours", 24)
                .when()
                .get("/api/device/absent-device/sensor-history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));

        given()
                .queryParam("days", 7)
                .when()
                .get("/api/device/absent-device/watering-logs")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void wateringLogsNoPlantsReturnsEmptyList() {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("no-plants");
        deviceRepository.save(device);

        given()
                .queryParam("days", 7)
                .when()
                .get("/api/device/no-plants/watering-logs")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void sensorHistoryDownsamplesTo200Points() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<SensorDataEntity> bulk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            SensorDataEntity item = SensorDataEntity.create();
            item.setDeviceId("many-points");
            item.setTimestamp(now.minusMinutes(i));
            item.setSoilMoisture(30.0 + (i % 5));
            item.setAirTemperature(20.0 + (i % 3));
            item.setAirHumidity(50.0 + (i % 4));
            bulk.add(item);
        }
        sensorDataRepository.saveAll(bulk);

        Response response = given()
                .queryParam("hours", 1000)
                .when()
                .get("/api/device/many-points/sensor-history")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<String> timestamps = response.jsonPath().getList("timestamp");
        Assertions.assertTrue(timestamps.size() <= 200);
        List<LocalDateTime> parsed = new ArrayList<>();
        for (String ts : timestamps) {
            parsed.add(LocalDateTime.parse(ts));
        }
        List<LocalDateTime> sorted = new ArrayList<>(parsed);
        sorted.sort(LocalDateTime::compareTo);
        Assertions.assertEquals(sorted, parsed);
    }

    @Test
    void sensorHistoryFiltersOutliers() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int i = 0; i < 10; i++) {
            SensorDataEntity normal = SensorDataEntity.create();
            normal.setDeviceId("outliers");
            normal.setTimestamp(now.minusMinutes(i));
            normal.setSoilMoisture(40.0);
            normal.setAirTemperature(22.0);
            normal.setAirHumidity(55.0);
            sensorDataRepository.save(normal);
        }

        SensorDataEntity outlier1 = SensorDataEntity.create();
        outlier1.setDeviceId("outliers");
        outlier1.setTimestamp(now.minusMinutes(200));
        outlier1.setSoilMoisture(150.0);
        outlier1.setAirTemperature(-273.0);
        outlier1.setAirHumidity(1000.0);
        sensorDataRepository.save(outlier1);

        SensorDataEntity outlier2 = SensorDataEntity.create();
        outlier2.setDeviceId("outliers");
        outlier2.setTimestamp(now.minusMinutes(201));
        outlier2.setSoilMoisture(-10.0);
        outlier2.setAirTemperature(999.0);
        outlier2.setAirHumidity(-5.0);
        sensorDataRepository.save(outlier2);

        given()
                .queryParam("hours", 500)
                .when()
                .get("/api/device/outliers/sensor-history")
                .then()
                .statusCode(200)
                .body("size()", equalTo(10))
                .body("[0].air_temperature", greaterThanOrEqualTo(-20.0f));
    }

    @Test
    void sensorHistoryValidationReturns422() {
        given()
                .queryParam("hours", "bad")
                .when()
                .get("/api/device/validate/sensor-history")
                .then()
                .statusCode(422);
    }

    @Test
    void wateringLogsValidationReturns422() {
        given()
                .queryParam("days", "bad")
                .when()
                .get("/api/device/validate/watering-logs")
                .then()
                .statusCode(422);
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
