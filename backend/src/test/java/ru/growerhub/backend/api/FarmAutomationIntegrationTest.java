package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.sensor.contract.SensorStatus;
import ru.growerhub.backend.sensor.contract.SensorType;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOwnedDeviceData;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST=", "SELF_SERVICE_ENABLED=true"}
)
class FarmAutomationIntegrationTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private AutomationFacade automationFacade;

    @MockBean
    private ZigbeeFacade zigbeeFacade;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        when(zigbeeFacade.getDevicesForUser(any(AuthenticatedUser.class))).thenReturn(List.of());
        when(zigbeeFacade.getDevicesForAutomation()).thenReturn(List.of());
        clearDatabase();
    }

    @Test
    void userOwnsMultipleFarmsAndCanMoveGreenhouseBetweenThem() {
        UserEntity admin = createUser("farm-admin@example.com", "admin");
        UserEntity other = createUser("farm-other@example.com", "user");
        String adminToken = buildToken(admin.getId());
        String otherToken = buildToken(other.getId());

        Integer firstFarmId = createFarm(adminToken, "Основное помещение");
        Integer secondFarmId = createFarm(adminToken, "Второе помещение");
        Integer otherFarmId = createFarm(otherToken, "Чужая ферма");
        Integer otherGreenhouseId = createGreenhouse(otherToken, otherFarmId, "Чужая теплица");
        Integer greenhouseId = createGreenhouse(adminToken, firstFarmId, "Теплица 1");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms", hasSize(2))
                .body("farms.find { it.id == " + firstFarmId + " }.greenhouses", hasSize(1))
                .body("farms.find { it.id == " + secondFarmId + " }.greenhouses", hasSize(0));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("""
                        {
                          "farm_id":%d,
                          "name":"Перенесённая теплица",
                          "enabled":true
                        }
                        """.formatted(secondFarmId))
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId)
                .then()
                .statusCode(200)
                .body("farms.find { it.id == " + firstFarmId + " }.greenhouses", hasSize(0))
                .body("farms.find { it.id == " + secondFarmId + " }.greenhouses[0].name",
                        equalTo("Перенесённая теплица"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("{\"name\":\"stolen\"}")
                .when()
                .put("/api/automation/farms/" + otherFarmId)
                .then()
                .statusCode(404)
                .body("detail", equalTo("Ферма не найдена"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("{\"name\":\"stolen\"}")
                .when()
                .put("/api/automation/greenhouses/" + otherGreenhouseId)
                .then()
                .statusCode(404)
                .body("detail", equalTo("Теплица не найдена"));
    }

    @Test
    void plantMovesBetweenGreenhousesAtomically() {
        UserEntity owner = createUser("farm-plants@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Моя ферма");
        Integer firstGreenhouseId = createGreenhouse(token, farmId, "Теплица 1");
        Integer secondGreenhouseId = createGreenhouse(token, farmId, "Теплица 2");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "name":"Томат",
                          "zone_id":%d,
                          "plant_type":"tomato"
                        }
                        """.formatted(firstGreenhouseId))
                .when()
                .post("/api/plants")
                .then()
                .statusCode(200)
                .body("zone.id", equalTo(firstGreenhouseId))
                .body("zone.name", equalTo("Теплица 1"))
                .body("zone.farm_id", equalTo(farmId))
                .body("zone.farm_name", equalTo("Моя ферма"));

        Integer plantId = jdbcTemplate.queryForObject(
                "SELECT id FROM plants WHERE name='Томат'",
                Integer.class
        );
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":" + secondGreenhouseId + "}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone.id", equalTo(secondGreenhouseId))
                .body("zone.name", equalTo("Теплица 2"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":null}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone", nullValue());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "name":"Огурец",
                          "zone_id":999999,
                          "plant_type":"cucumber"
                        }
                        """)
                .when()
                .post("/api/plants")
                .then()
                .statusCode(404);

        Integer rolledBackPlants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plants WHERE name='Огурец'",
                Integer.class
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, rolledBackPlants);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"zone_id\":" + firstGreenhouseId + "}")
                .when()
                .patch("/api/plants/" + plantId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/automation/greenhouses/" + firstGreenhouseId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/plants/" + plantId)
                .then()
                .statusCode(200)
                .body("zone", nullValue());
    }

    @Test
    void physicalSensorNeedsExplicitReassignmentBetweenGreenhouses() {
        UserEntity owner = createUser("farm-slots@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Моя ферма");
        Integer firstGreenhouseId = createGreenhouse(token, farmId, "Теплица 1");
        Integer secondGreenhouseId = createGreenhouse(token, farmId, "Теплица 2");
        SensorEntity sensor = createSensor(owner, null);

        String slot = """
                {
                  "role":"AIR_TEMPERATURE_SENSOR",
                  "source_type":"NATIVE_SENSOR",
                  "native_sensor_id":%d
                }
                """.formatted(sensor.getId());
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "]}")
                .when()
                .put("/api/automation/greenhouses/" + firstGreenhouseId + "/slots")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses.find { it.id == " + firstGreenhouseId + " }.slots",
                        hasSize(1));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "]}")
                .when()
                .put("/api/automation/greenhouses/" + secondGreenhouseId + "/slots")
                .then()
                .statusCode(409);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"slots\":[" + slot + "],\"reassign\":true}")
                .when()
                .put("/api/automation/greenhouses/" + secondGreenhouseId + "/slots")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses.find { it.id == " + firstGreenhouseId + " }.slots",
                        hasSize(0))
                .body("farms[0].greenhouses.find { it.id == " + secondGreenhouseId
                                + " }.slots[0].role",
                        equalTo("AIR_TEMPERATURE_SENSOR"));
    }

    @Test
    void greenhousePublishesCoolingRequestWithoutAnyAirConditioner() {
        UserEntity owner = createUser("farm-cooling@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Помещение");
        Integer reserveFarmId = createFarm(token, "Резервное помещение");
        Integer greenhouseId = createGreenhouse(token, farmId, "Горячая теплица");
        SensorEntity sensor = createSensor(owner, 32.0);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"slots":[{
                          "role":"AIR_TEMPERATURE_SENSOR",
                          "source_type":"NATIVE_SENSOR",
                          "native_sensor_id":%d
                        }]}
                        """.formatted(sensor.getId()))
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/slots")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"BOX_CLIMATE",
                          "enabled":true,
                          "config":{
                            "ac_request_above_c":30,
                            "ac_clear_below_c":27
                          }
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].readiness.BOX_CLIMATE.ready", equalTo(true));

        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms.find { it.id == " + farmId + " }.slots", hasSize(0))
                .body("farms.find { it.id == " + farmId + " }.greenhouses[0].slots", hasSize(1))
                .body("farms.find { it.id == " + farmId
                                + " }.greenhouses[0].states.find { it.scenario_type == 'BOX_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(true))
                .body("farms.find { it.id == " + farmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(true))
                .body("farms.find { it.id == " + farmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.pending_request_count",
                        equalTo(1));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "farm_id":%d,
                          "name":"Горячая теплица",
                          "enabled":true
                        }
                        """.formatted(reserveFarmId))
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId)
                .then()
                .statusCode(200)
                .body("farms.find { it.id == " + farmId
                                + " }.scenarios.find { it.scenario_type == 'ROOM_CLIMATE' }.enabled",
                        equalTo(false))
                .body("farms.find { it.id == " + reserveFarmId
                                + " }.scenarios.find { it.scenario_type == 'ROOM_CLIMATE' }.enabled",
                        equalTo(true));

        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms.find { it.id == " + reserveFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.pending_request_count",
                        equalTo(1));
    }

    @Test
    void localAirConditionerHandlesOwnRequestAndFarmHandlesOnlyUncoveredRequest() {
        UserEntity owner = createUser("farm-cooling-routing@example.com", "user");
        String token = buildToken(owner.getId());
        Integer localFarmId = createFarm(token, "Ферма с локальным кондиционером");
        Integer sharedFarmId = createFarm(token, "Ферма с общим кондиционером");
        Integer localGreenhouseId = createGreenhouse(token, localFarmId, "Локальная теплица");
        Integer sharedGreenhouseId = createGreenhouse(token, sharedFarmId, "Общая теплица");
        SensorEntity localSensor = createSensor(owner, 32.0);
        SensorEntity sharedSensor = createSensor(owner, 33.0);
        UUID coordinatorId = UUID.randomUUID();
        int coordinatorInternalId = 41;
        String localAcIeee = "0x00124b0000000001";
        String sharedAcIeee = "0x00124b0000000002";
        List<ZigbeeOwnedDeviceData> switches = List.of(
                switchDevice(coordinatorInternalId, coordinatorId, localAcIeee, "Локальный кондиционер"),
                switchDevice(coordinatorInternalId, coordinatorId, sharedAcIeee, "Общий кондиционер")
        );
        when(zigbeeFacade.getDevicesForUser(any(AuthenticatedUser.class))).thenReturn(switches);
        when(zigbeeFacade.getDevicesForAutomation()).thenReturn(switches);

        replaceGreenhouseClimateSlots(
                token,
                localGreenhouseId,
                localSensor.getId(),
                coordinatorId,
                localAcIeee
        );
        replaceGreenhouseClimateSlots(
                token,
                sharedGreenhouseId,
                sharedSensor.getId(),
                null,
                null
        );
        replaceFarmAirConditioner(token, sharedFarmId, coordinatorId, sharedAcIeee);
        enableGreenhouseClimate(token, localGreenhouseId);
        enableGreenhouseClimate(token, sharedGreenhouseId);

        automationFacade.evaluateAll();

        verify(zigbeeFacade).setAutomationDeviceProperty(
                coordinatorInternalId,
                localAcIeee,
                "state",
                "ON"
        );
        verify(zigbeeFacade).setAutomationDeviceProperty(
                coordinatorInternalId,
                sharedAcIeee,
                "state",
                "ON"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms.find { it.id == " + localFarmId
                                + " }.greenhouses[0].states.find { it.scenario_type == 'BOX_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(true))
                .body("farms.find { it.id == " + localFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.pending_request_count",
                        equalTo(0))
                .body("farms.find { it.id == " + localFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(false))
                .body("farms.find { it.id == " + sharedFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.pending_request_count",
                        equalTo(1))
                .body("farms.find { it.id == " + sharedFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(true));
    }

    @Test
    void enabledLightScenarioUsesReadableReason() {
        UserEntity owner = createUser("farm-readiness@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Моя ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "scenarios":[{
                            "scenario_type":"LIGHT_SCHEDULE",
                            "enabled":true,
                            "config":{}
                          }]
                        }
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(409)
                .body("detail", equalTo("Нужен Zigbee-выключатель света"));
    }

    private void replaceGreenhouseClimateSlots(
            String token,
            Integer greenhouseId,
            Integer sensorId,
            UUID coordinatorId,
            String airConditionerIeee
    ) {
        String airConditionerSlot = airConditionerIeee != null
                ? """
                  ,{
                    "role":"AC_SWITCH",
                    "source_type":"ZIGBEE_DEVICE",
                    "zigbee_coordinator_id":"%s",
                    "zigbee_ieee_address":"%s"
                  }
                  """.formatted(coordinatorId, airConditionerIeee)
                : "";
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"slots":[{
                          "role":"AIR_TEMPERATURE_SENSOR",
                          "source_type":"NATIVE_SENSOR",
                          "native_sensor_id":%d
                        }%s]}
                        """.formatted(sensorId, airConditionerSlot))
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/slots")
                .then()
                .statusCode(200);
    }

    private void replaceFarmAirConditioner(
            String token,
            Integer farmId,
            UUID coordinatorId,
            String airConditionerIeee
    ) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"slots":[{
                          "role":"AC_SWITCH",
                          "source_type":"ZIGBEE_DEVICE",
                          "zigbee_coordinator_id":"%s",
                          "zigbee_ieee_address":"%s"
                        }]}
                        """.formatted(coordinatorId, airConditionerIeee))
                .when()
                .put("/api/automation/farms/" + farmId + "/slots")
                .then()
                .statusCode(200);
    }

    private void enableGreenhouseClimate(String token, Integer greenhouseId) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"BOX_CLIMATE",
                          "enabled":true,
                          "config":{
                            "ac_request_above_c":30,
                            "ac_clear_below_c":27
                          }
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(200);
    }

    private ZigbeeOwnedDeviceData switchDevice(
            int coordinatorInternalId,
            UUID coordinatorId,
            String ieeeAddress,
            String name
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ZigbeeFeatureData state = new ZigbeeFeatureData(
                "binary",
                "state",
                "state",
                "Состояние",
                null,
                7,
                null,
                List.of("ON", "OFF"),
                null,
                null,
                null,
                "ON",
                "OFF",
                "TOGGLE",
                null,
                "OFF"
        );
        return new ZigbeeOwnedDeviceData(
                coordinatorInternalId,
                coordinatorId,
                "Тестовый координатор",
                new ZigbeeDeviceData(
                        null,
                        ieeeAddress,
                        name,
                        "Router",
                        true,
                        false,
                        false,
                        null,
                        null,
                        null,
                        List.of(state),
                        List.of(),
                        List.of(state),
                        Map.of("state", "OFF"),
                        "online",
                        now,
                        now
                )
        );
    }

    private Integer createFarm(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\",\"enabled\":true}")
                .when()
                .post("/api/automation/farms")
                .then()
                .statusCode(200)
                .extract()
                .path("farms.find { it.name == '" + name + "' }.id");
    }

    private Integer createGreenhouse(String token, Integer farmId, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\",\"enabled\":true}")
                .when()
                .post("/api/automation/farms/" + farmId + "/greenhouses")
                .then()
                .statusCode(200)
                .extract()
                .path("farms.find { it.id == " + farmId
                        + " }.greenhouses.find { it.name == '" + name + "' }.id");
    }

    private SensorEntity createSensor(UserEntity owner, Double value) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("farm-native-" + owner.getId() + "-" + UUID.randomUUID());
        device.setName("Farm native device");
        device.setUserId(owner.getId());
        device.setLastSeen(now);
        device = deviceRepository.save(device);

        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(device.getId());
        sensor.setType(SensorType.AIR_TEMPERATURE);
        sensor.setChannel(0);
        sensor.setLabel("Температура");
        sensor.setDetected(true);
        sensor.setStatus(SensorStatus.OK);
        sensor.setCreatedAt(now);
        sensor.setUpdatedAt(now);
        sensor = sensorRepository.save(sensor);

        if (value != null) {
            SensorReadingEntity reading = SensorReadingEntity.create();
            reading.setSensor(sensor);
            reading.setTs(now);
            reading.setValueNumeric(value);
            reading.setCreatedAt(now);
            sensorReadingRepository.save(reading);
        }
        return sensor;
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
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
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM sensor_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM device_service_events");
        jdbcTemplate.update("DELETE FROM devices");
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
