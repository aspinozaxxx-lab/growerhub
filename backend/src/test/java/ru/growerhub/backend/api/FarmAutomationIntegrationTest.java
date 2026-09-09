package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import ru.growerhub.backend.zigbee.contract.ZigbeePowerStatistics;

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
    void resourceStatisticsChecksOwnerAdminAndRole() {
        UserEntity owner = createUser("statistics-owner@example.com", "user");
        UserEntity other = createUser("statistics-other@example.com", "user");
        UserEntity admin = createUser("statistics-admin@example.com", "admin");
        String ownerToken = buildToken(owner.getId());
        Integer farmId = createFarm(ownerToken, "Ферма со статистикой");
        Integer resourceId = insertZigbeeResource(farmId, "AC_SWITCH");
        Integer exhaustResourceId = insertZigbeeResource(farmId, "EXHAUST_SWITCH");
        Integer lightResourceId = insertZigbeeResource(farmId, "LIGHT_SWITCH");
        Integer unsupportedResourceId = insertZigbeeResource(farmId, "AIR_TEMPERATURE_SENSOR");
        when(zigbeeFacade.getPowerStatisticsForAutomation(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ZigbeePowerStatistics("power", "W", List.of(), true, List.of()));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/automation/resources/" + resourceId + "/statistics?hours=24")
                .then()
                .statusCode(200)
                .body("chart_kind", equalTo("power"))
                .body("chart_unit", equalTo("W"))
                .body("energy_supported", equalTo(true));

        for (Integer allowedResourceId : List.of(exhaustResourceId, lightResourceId)) {
            given()
                    .header("Authorization", "Bearer " + ownerToken)
                    .when()
                    .get("/api/automation/resources/" + allowedResourceId + "/statistics?hours=24")
                    .then()
                    .statusCode(200)
                    .body("chart_kind", equalTo("power"));
        }

        given()
                .header("Authorization", "Bearer " + buildToken(other.getId()))
                .when()
                .get("/api/automation/resources/" + resourceId + "/statistics?hours=24")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Ресурс не найден"));

        given()
                .header("Authorization", "Bearer " + buildToken(admin.getId()))
                .when()
                .get("/api/admin/automation/resources/" + resourceId + "/statistics?hours=24")
                .then()
                .statusCode(200)
                .body("chart_kind", equalTo("power"));

        given()
                .header("Authorization", "Bearer " + ownerToken)
                .when()
                .get("/api/automation/resources/" + unsupportedResourceId + "/statistics?hours=24")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Статистика мощности недоступна для ресурса"));
    }

    @Test
    void plantMovesBetweenGreenhousesAtomically() {
        UserEntity owner = createUser("farm-plants@example.com", "user");
        UserEntity other = createUser("farm-plants-other@example.com", "user");
        String token = buildToken(owner.getId());
        String otherToken = buildToken(other.getId());
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
                .header("Authorization", "Bearer " + otherToken)
                .contentType("application/json")
                .body("{\"rate_ml_per_hour\":120}")
                .when()
                .patch("/api/automation/greenhouses/" + firstGreenhouseId
                        + "/plants/" + plantId + "/watering-rate")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Теплица не найдена"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"rate_ml_per_hour\":120}")
                .when()
                .patch("/api/automation/greenhouses/" + firstGreenhouseId
                        + "/plants/" + plantId + "/watering-rate")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses.find { it.id == " + firstGreenhouseId
                                + " }.plants[0].rate_ml_per_hour",
                        equalTo(120));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"rate_ml_per_hour\":0}")
                .when()
                .patch("/api/automation/greenhouses/" + firstGreenhouseId
                        + "/plants/" + plantId + "/watering-rate")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Поле rate_ml_per_hour должно быть больше нуля"));

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
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses.find { it.id == " + firstGreenhouseId
                                + " }.plants",
                        hasSize(0))
                .body("farms[0].greenhouses.find { it.id == " + secondGreenhouseId
                                + " }.plants[0].rate_ml_per_hour",
                        equalTo(120));

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
                            "min_c":20,
                            "off_delay_minutes":5,
                            "min_toggle_minutes":5,
                            "ac_request_above_c":30,
                            "ac_clear_below_c":27
                          }
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].readiness.BOX_CLIMATE.ready", equalTo(true))
                .body("farms[0].greenhouses[0].scenarios"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.config.min_c",
                        nullValue())
                .body("farms[0].greenhouses[0].scenarios"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.config.off_delay_minutes",
                        nullValue())
                .body("farms[0].greenhouses[0].scenarios"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.config.min_toggle_minutes",
                        nullValue());

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
                                + " }.greenhouses[0].states.find { it.scenario_type == 'BOX_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("unavailable"))
                .body("farms.find { it.id == " + farmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".ac_request_active",
                        equalTo(true))
                .body("farms.find { it.id == " + farmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("unavailable"))
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
                                + " }.greenhouses[0].states.find { it.scenario_type == 'BOX_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("switching_on"))
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
                        equalTo(true))
                .body("farms.find { it.id == " + sharedFarmId
                                + " }.states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("switching_on"));
    }

    @Test
    void farmAirConditionerSwitchesOffImmediatelyWhenRequestClears() {
        UserEntity owner = createUser("farm-ac-immediate-off@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");
        SensorEntity sensor = createSensor(owner, 32.0);
        UUID coordinatorId = UUID.randomUUID();
        int coordinatorInternalId = 42;
        String acIeee = "0x00124b0000000010";
        List<ZigbeeOwnedDeviceData> switches = List.of(
                switchDevice(coordinatorInternalId, coordinatorId, acIeee, "Кондиционер", "ON")
        );
        when(zigbeeFacade.getDevicesForUser(any(AuthenticatedUser.class))).thenReturn(switches);
        when(zigbeeFacade.getDevicesForAutomation()).thenReturn(switches);

        replaceGreenhouseClimateSlots(token, greenhouseId, sensor.getId(), null, null);
        replaceFarmAirConditioner(token, farmId, coordinatorId, acIeee);
        enableGreenhouseClimate(token, greenhouseId);
        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("cooling"))
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.request_count",
                        equalTo(1));

        addSensorReading(sensor, 20.0);
        automationFacade.evaluateAll();

        verify(zigbeeFacade).setAutomationDeviceProperty(
                coordinatorInternalId,
                acIeee,
                "state",
                "OFF"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.phase",
                        equalTo("switching_off"))
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.desired_state",
                        equalTo("OFF"))
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.request_count",
                        equalTo(0))
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".runtime.ac_control.last_command_at",
                        org.hamcrest.Matchers.notNullValue())
                .body("farms[0].states.find { it.scenario_type == 'ROOM_CLIMATE' }"
                                + ".ac_next_transition_at",
                        nullValue());
    }

    @Test
    void localAirConditionerSwitchesOffImmediatelyWithoutRequest() {
        UserEntity owner = createUser("greenhouse-ac-immediate-off@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");
        SensorEntity sensor = createSensor(owner, 20.0);
        UUID coordinatorId = UUID.randomUUID();
        int coordinatorInternalId = 43;
        String acIeee = "0x00124b0000000011";
        List<ZigbeeOwnedDeviceData> switches = List.of(
                switchDevice(coordinatorInternalId, coordinatorId, acIeee, "Кондиционер", "ON")
        );
        when(zigbeeFacade.getDevicesForUser(any(AuthenticatedUser.class))).thenReturn(switches);
        when(zigbeeFacade.getDevicesForAutomation()).thenReturn(switches);

        replaceGreenhouseClimateSlots(token, greenhouseId, sensor.getId(), coordinatorId, acIeee);
        enableGreenhouseClimate(token, greenhouseId);
        automationFacade.evaluateAll();

        verify(zigbeeFacade).setAutomationDeviceProperty(
                coordinatorInternalId,
                acIeee,
                "state",
                "OFF"
        );
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.runtime.ac_control.phase",
                        equalTo("switching_off"))
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }"
                                + ".runtime.ac_control.request_count",
                        equalTo(0));
    }

    @Test
    void disabledClimateDoesNotControlRunningAirConditioner() {
        UserEntity owner = createUser("greenhouse-ac-disabled@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");
        SensorEntity sensor = createSensor(owner, 20.0);
        UUID coordinatorId = UUID.randomUUID();
        int coordinatorInternalId = 44;
        String acIeee = "0x00124b0000000012";
        List<ZigbeeOwnedDeviceData> switches = List.of(
                switchDevice(coordinatorInternalId, coordinatorId, acIeee, "Кондиционер", "ON")
        );
        when(zigbeeFacade.getDevicesForUser(any(AuthenticatedUser.class))).thenReturn(switches);
        when(zigbeeFacade.getDevicesForAutomation()).thenReturn(switches);

        replaceGreenhouseClimateSlots(token, greenhouseId, sensor.getId(), coordinatorId, acIeee);
        automationFacade.evaluateAll();

        verify(zigbeeFacade, never()).setAutomationDeviceProperty(
                coordinatorInternalId,
                acIeee,
                "state",
                "OFF"
        );
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.runtime.ac_control.phase",
                        equalTo("disabled"));
    }

    @Test
    void climateUsesOnlyExplicitThresholdsAndKeepsHysteresis() {
        UserEntity owner = createUser("farm-climate-thresholds@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");
        SensorEntity sensor = createSensor(owner, 27.0);
        replaceGreenhouseClimateSlots(token, greenhouseId, sensor.getId(), null, null);
        enableGreenhouseClimate(token, greenhouseId);
        automationFacade.evaluateAll();

        jdbcTemplate.update("""
                UPDATE automation_scenario_states
                SET runtime_json=?
                WHERE scope_type='BOX' AND scope_id=? AND scenario_type='BOX_CLIMATE'
                """, """
                {"last_temperature":27.0,"last_temperature_at":"%s"}
                """.formatted(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(6)), greenhouseId);
        addSensorReading(sensor, 29.0);
        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.ac_request_active",
                        equalTo(false))
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.runtime.last_temperature",
                        nullValue());

        addSensorReading(sensor, 31.0);
        automationFacade.evaluateAll();
        addSensorReading(sensor, 29.0);
        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.ac_request_active",
                        equalTo(true));

        addSensorReading(sensor, 27.0);
        automationFacade.evaluateAll();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/automation/farms")
                .then()
                .statusCode(200)
                .body("farms[0].greenhouses[0].states"
                                + ".find { it.scenario_type == 'BOX_CLIMATE' }.ac_request_active",
                        equalTo(false));
    }

    @Test
    void climateThresholdsMustBeNumericAndOrdered() {
        UserEntity owner = createUser("farm-climate-validation@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "Ферма");
        Integer greenhouseId = createGreenhouse(token, farmId, "Теплица");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"BOX_CLIMATE",
                          "enabled":false,
                          "config":{"max_c":25,"exhaust_off_below_c":25}
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(400)
                .body("detail", equalTo(
                        "Порог выключения обдува должен быть ниже порога включения"
                ));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"BOX_CLIMATE",
                          "enabled":false,
                          "config":{"ac_request_above_c":27,"ac_clear_below_c":27}
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(400)
                .body("detail", equalTo(
                        "Порог снятия запроса должен быть ниже порога его создания"
                ));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"BOX_CLIMATE",
                          "enabled":false,
                          "config":{"max_c":"тепло"}
                        }]}
                        """)
                .when()
                .put("/api/automation/greenhouses/" + greenhouseId + "/scenarios")
                .then()
                .statusCode(400)
                .body("detail", equalTo("Поле max_c должно быть числом"));
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

    @Test
    void bulkDisablePreservesEveryConfigAndDoesNotAffectOtherOwnersEvenForAdmin() {
        UserEntity admin = createUser("bulk-admin@example.com", "admin");
        UserEntity other = createUser("bulk-other@example.com", "user");
        String token = buildToken(admin.getId());
        Integer farmId = createFarm(token, "A");
        Integer secondFarmId = createFarm(token, "B");
        Integer firstId = createGreenhouse(token, farmId, "A");
        Integer secondId = createGreenhouse(token, secondFarmId, "B");
        String otherToken = buildToken(other.getId());
        Integer otherId = createGreenhouse(otherToken, createFarm(otherToken, "Other"), "Other");
        String lightConfig = "{\"start_time\":\"21:15\",\"end_time\":\"07:45\",\"extra\":42}";
        for (Integer id : List.of(firstId, secondId, otherId)) {
            insertScenario("BOX", id, "LIGHT_SCHEDULE", true, lightConfig);
            insertScenario("BOX", id, "WATERING", true, "{\"run_seconds\":45,\"daily_max_seconds\":400}");
            insertScenario("BOX", id, "BOX_CLIMATE", true, "{\"max_c\":28}");
        }
        insertScenario("ROOM", farmId, "ROOM_CLIMATE", true, "{\"custom\":true}");
        List<Map<String, Object>> originalConfigs = jdbcTemplate.queryForList(
                "SELECT id, config_json FROM automation_scenario_configs ORDER BY id");

        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body("{\"enabled\":false}").when().put("/api/automation/scenarios/enabled")
                .then().statusCode(200).body("farms", hasSize(2));

        assertEquals(originalConfigs, jdbcTemplate.queryForList(
                "SELECT id, config_json FROM automation_scenario_configs ORDER BY id"));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_scenario_configs WHERE enabled=true", Integer.class));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_scenario_configs WHERE scope_type='BOX' AND scope_id=? AND enabled=true",
                Integer.class, otherId));
    }

    @Test
    void bulkEnableSkipsUnavailableAndInactiveScopesAndRestoresConfiguredValues() {
        UserEntity owner = createUser("bulk-enable@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "A");
        Integer greenhouseId = createGreenhouse(token, farmId, "A");
        Integer unreadyId = createGreenhouse(token, farmId, "B");
        Integer inactiveId = createGreenhouse(token, farmId, "C");
        SensorEntity sensor = createSensor(owner, 26.0);
        replaceGreenhouseClimateSlots(token, greenhouseId, sensor.getId(), null, null);
        SensorEntity inactiveSensor = createSensor(owner, 26.0);
        replaceGreenhouseClimateSlots(token, inactiveId, inactiveSensor.getId(), null, null);
        jdbcTemplate.update("UPDATE automation_boxes SET enabled=false WHERE id=?", inactiveId);
        String config = "{\"max_c\":31,\"exhaust_off_below_c\":28,\"ac_request_above_c\":33,\"ac_clear_below_c\":29}";
        insertScenario("BOX", greenhouseId, "BOX_CLIMATE", false, config);

        for (boolean enabled : List.of(true, false, true)) {
            given().header("Authorization", "Bearer " + token).contentType("application/json")
                    .body(Map.of("enabled", enabled)).when().put("/api/automation/scenarios/enabled")
                    .then().statusCode(200)
                    .body("farms[0].greenhouses.find { it.id == " + greenhouseId
                            + " }.scenarios.find { it.scenario_type == 'BOX_CLIMATE' }.enabled", equalTo(enabled))
                    .body("farms[0].scenarios.find { it.scenario_type == 'ROOM_CLIMATE' }.enabled", equalTo(enabled))
                    .body("farms[0].greenhouses.find { it.id == " + unreadyId
                            + " }.scenarios.findAll { it.enabled }", hasSize(0))
                    .body("farms[0].greenhouses.find { it.id == " + inactiveId
                            + " }.scenarios.findAll { it.enabled }", hasSize(0));
            assertEquals(config, jdbcTemplate.queryForObject(
                    "SELECT config_json FROM automation_scenario_configs WHERE scope_type='BOX' AND scope_id=? AND scenario_type='BOX_CLIMATE'",
                    String.class, greenhouseId));
        }
    }

    @Test
    void bulkEnableRollsBackWhenLaterScenarioValidationFails() {
        UserEntity owner = createUser("bulk-rollback@example.com", "user");
        String token = buildToken(owner.getId());
        Integer farmId = createFarm(token, "A");
        Integer firstId = createGreenhouse(token, farmId, "A");
        Integer secondId = createGreenhouse(token, farmId, "B");
        for (Integer id : List.of(firstId, secondId)) {
            SensorEntity sensor = createSensor(owner, 26.0);
            replaceGreenhouseClimateSlots(token, id, sensor.getId(), null, null);
        }
        insertScenario("BOX", secondId, "BOX_CLIMATE", false,
                "{\"max_c\":25,\"exhaust_off_below_c\":30}");
        given().header("Authorization", "Bearer " + token).contentType("application/json")
                .body("{\"enabled\":true}").when().put("/api/automation/scenarios/enabled")
                .then().statusCode(400);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM automation_scenario_configs WHERE enabled=true", Integer.class));
    }

    @Test
    void bulkToggleRequiresExplicitBoolean() {
        UserEntity owner = createUser("bulk-missing@example.com", "user");
        given().header("Authorization", "Bearer " + buildToken(owner.getId())).contentType("application/json")
                .body("{}").when().put("/api/automation/scenarios/enabled")
                .then().statusCode(400).body("detail", equalTo("Поле enabled обязательно"));
    }

    private void insertScenario(String scope, Integer id, String type, boolean enabled, String config) {
        jdbcTemplate.update("""
                INSERT INTO automation_scenario_configs
                (scope_type, scope_id, scenario_type, enabled, config_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, scope, id, type, enabled, config);
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
        return switchDevice(coordinatorInternalId, coordinatorId, ieeeAddress, name, "OFF");
    }

    private ZigbeeOwnedDeviceData switchDevice(
            int coordinatorInternalId,
            UUID coordinatorId,
            String ieeeAddress,
            String name,
            String currentState
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
                currentState
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
                        Map.of("state", currentState),
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

    private void addSensorReading(SensorEntity sensor, Double value) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SensorReadingEntity reading = SensorReadingEntity.create();
        reading.setSensor(sensor);
        reading.setTs(now);
        reading.setValueNumeric(value);
        reading.setCreatedAt(now);
        sensorReadingRepository.saveAndFlush(reading);
    }

    private UserEntity createUser(String email, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return userRepository.save(UserEntity.create(email, null, role, true, now, now));
    }

    private Integer insertZigbeeResource(Integer scopeId, String role) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                        INSERT INTO automation_resource_bindings(
                            scope_type, scope_id, role, source_type, zigbee_coordinator_id,
                            zigbee_ieee_address, zigbee_property, command_property,
                            on_value, off_value, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "ROOM",
                scopeId,
                role,
                "ZIGBEE_DEVICE",
                1,
                "0xstatistics-" + role,
                "state",
                "state",
                "ON",
                "OFF",
                now,
                now
        );
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM automation_resource_bindings",
                Integer.class
        );
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
