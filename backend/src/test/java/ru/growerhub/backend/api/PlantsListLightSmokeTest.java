package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;

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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.sensor.contract.SensorType;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlantsListLightSmokeTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorPlantBindingRepository sensorPlantBindingRepository;

    @Autowired
    private PumpPlantBindingRepository pumpPlantBindingRepository;

    @Autowired
    private PumpRepository pumpRepository;

    @MockBean
    private AdvisorFacade advisorFacade;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        Mockito.reset(advisorFacade);
    }

    @Test
    void listPlantsLightContractNoAdvisor() {
        UserEntity owner = createUser("light-owner@example.com", "user");
        String token = buildToken(owner.getId());
        PlantEntity plant = createPlant(owner, "Light");
        DeviceEntity device = createDevice(owner, "dev-light");

        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(device.getId());
        sensor.setType(SensorType.SOIL_MOISTURE);
        sensor.setChannel(0);
        sensor.setDetected(true);
        sensorRepository.save(sensor);

        SensorPlantBindingEntity sensorBinding = SensorPlantBindingEntity.create();
        sensorBinding.setPlantId(plant.getId());
        sensorBinding.setSensor(sensor);
        sensorPlantBindingRepository.save(sensorBinding);

        PumpEntity pump = PumpEntity.create();
        pump.setDeviceId(device.getId());
        pump.setChannel(0);
        pumpRepository.save(pump);

        PumpPlantBindingEntity pumpBinding = PumpPlantBindingEntity.create();
        pumpBinding.setPlantId(plant.getId());
        pumpBinding.setPump(pump);
        pumpBinding.setRateMlPerHour(2000);
        pumpPlantBindingRepository.save(pumpBinding);

        Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants")
                .then()
                .statusCode(200)
                .extract()
                .response();

        List<Map<String, Object>> plants = response.jsonPath().getList("$");
        Assertions.assertEquals(1, plants.size());
        Map<String, Object> payload = plants.get(0);
        Assertions.assertFalse(payload.containsKey("watering_previous"));
        Assertions.assertFalse(payload.containsKey("watering_advice"));

        List<Map<String, Object>> sensors = response.jsonPath().getList("[0].sensors");
        Assertions.assertEquals(1, sensors.size());
        Assertions.assertFalse(sensors.get(0).containsKey("bound_plants"));

        List<Map<String, Object>> pumps = response.jsonPath().getList("[0].pumps");
        Assertions.assertEquals(1, pumps.size());
        Assertions.assertFalse(pumps.get(0).containsKey("bound_plants"));

        Mockito.verifyNoInteractions(advisorFacade);

        System.out.println("PLANTS_RESPONSE=" + response.asString());
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, role, true, now, now);
        return userRepository.save(user);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        PlantEntity plant = PlantEntity.create();
        plant.setName(name);
        plant.setUserId(owner != null ? owner.getId() : null);
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        plant.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plant.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return plantRepository.save(plant);
    }

    private DeviceEntity createDevice(UserEntity owner, String deviceId) {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId(deviceId);
        device.setName("Device " + deviceId);
        device.setUserId(owner != null ? owner.getId() : null);
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