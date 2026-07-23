package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "MQTT_HOST=",
                "automation.workerPeriodMs=3600000",
                "automation.wateringWorkerPeriodMs=3600000"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AdminAutomationControllerIntegrationTest.TestPublisherConfig.class)
class AdminAutomationControllerIntegrationTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZigbeeFacade zigbeeFacade;

    @Autowired
    private ZigbeeCoordinatorRepository coordinatorRepository;

    @Autowired
    private AutomationFacade automationFacade;

    @Autowired
    private DeviceFacade deviceFacade;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SpyBean
    private PumpFacade pumpFacade;

    @Autowired
    private TestPublisher testPublisher;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        seedLegacyCoordinator();
        seedZigbee();
        testPublisher.clear();
    }

    @Test
    void overviewRequiresAdmin() {
        UserEntity user = createUser("automation-user@example.com", "user");
        String token = buildToken(user.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/automation")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void manualWateringOverviewRequiresAdmin() {
        UserEntity user = createUser("manual-watering-user@example.com", "user");

        given()
                .header("Authorization", "Bearer " + buildToken(user.getId()))
                .when()
                .get("/api/admin/manual-watering")
                .then()
                .statusCode(403)
                .body("detail", equalTo("Nedostatochno prav"));
    }

    @Test
    void createsRoomAndBoxWithDisabledDefaultScenarios() {
        UserEntity admin = createUser("automation-admin@example.com", "admin");
        String token = buildToken(admin.getId());

        Integer roomId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Room A\"}")
                .when()
                .post("/api/admin/automation/rooms")
                .then()
                .statusCode(200)
                .body("rooms", hasSize(1))
                .body("rooms[0].name", equalTo("Room A"))
                .body("rooms[0].scenarios[0].scenario_type", equalTo("ROOM_CLIMATE"))
                .body("rooms[0].scenarios[0].enabled", equalTo(false))
                .extract()
                .path("rooms[0].id");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"Box A\"}")
                .when()
                .post("/api/admin/automation/rooms/" + roomId + "/boxes")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes", hasSize(1))
                .body("rooms[0].boxes[0].name", equalTo("Box A"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("BOX_CLIMATE"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("LIGHT_SCHEDULE"))
                .body("rooms[0].boxes[0].scenarios.scenario_type", hasItem("WATERING"))
                .body("rooms[0].boxes[0].scenarios.find { it.scenario_type == 'LIGHT_SCHEDULE' }.enabled", equalTo(false));
    }

    @Test
    void lightResourceAcceptsOnlyZigbeeWritableState() {
        UserEntity admin = createUser("automation-zigbee-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room B");
        Integer boxId = createBox(token, roomId, "Box B");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"LIGHT_SWITCH","source_type":"NATIVE_PUMP","native_pump_id":1}
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(400)
                .body("detail", equalTo("LIGHT_SWITCH v v1 dolzhen byt' Zigbee switch"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"LIGHT_SWITCH",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df3",
                            "zigbee_property":"state",
                            "command_property":"state",
                            "on_value":"ON",
                            "off_value":"OFF"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].role", equalTo("LIGHT_SWITCH"))
                .body("rooms[0].boxes[0].resources[0].source_type", equalTo("ZIGBEE_DEVICE"))
                .body("rooms[0].boxes[0].resources[0].zigbee_ieee_address", equalTo("0xa4c13895af2c1df3"))
                .body("rooms[0].boxes[0].resources[0].zigbee_property", equalTo("state"))
                .body("rooms[0].boxes[0].resources[0].command_property", equalTo("state"))
                .body("rooms[0].boxes[0].resources[0].ready", equalTo(true))
                .body("rooms[0].boxes[0].readiness.LIGHT_SCHEDULE.ready", equalTo(true));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"AIR_TEMPERATURE_SENSOR",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c138ccd6b42d0c",
                            "zigbee_property":"temperature"
                          },
                          {
                            "role":"LIGHT_SWITCH",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df3",
                            "zigbee_property":"state",
                            "command_property":"state",
                            "on_value":"ON",
                            "off_value":"OFF"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources", hasSize(2))
                .body("rooms[0].boxes[0].resources.find { it.role == 'AIR_TEMPERATURE_SENSOR' }.zigbee_ieee_address", equalTo("0xa4c138ccd6b42d0c"))
                .body("rooms[0].boxes[0].resources.find { it.role == 'AIR_TEMPERATURE_SENSOR' }.zigbee_property", equalTo("temperature"))
                .body("rooms[0].boxes[0].resources.find { it.role == 'LIGHT_SWITCH' }.zigbee_property", equalTo("state"));
    }

    @Test
    void leakSensorResourceAcceptsZigbeeReadableWaterLeakAndShowsConnectionWarning() {
        UserEntity admin = createUser("automation-leak-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Leak");
        Integer boxId = createBox(token, roomId, "Box Leak");
        seedLeakState(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10), false);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"LEAK_SENSOR",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df4",
                            "zigbee_property":"water_leak"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].role", equalTo("LEAK_SENSOR"))
                .body("rooms[0].boxes[0].resources[0].ready", equalTo(true))
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"))
                .body("rooms[0].boxes[0].resources[0].connection_message", equalTo("нет связи"));
    }

    @Test
    void nativeSensorConnectionUsesDeviceLastSeenInsteadOfLastReading() {
        UserEntity admin = createUser("automation-native-fresh-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Native Fresh");
        Integer boxId = createBox(token, roomId, "Box Native Fresh");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Integer sensorId = seedNativeSensor(
                admin.getId(),
                "native-fresh-device",
                now.minusMinutes(19),
                now.minusMinutes(20),
                "OK"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"AIR_TEMPERATURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d}
                        ]}
                        """.formatted(sensorId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("ok"));
    }

    @Test
    void nativeSensorConnectionWarnsWhenDeviceLastSeenIsStale() {
        UserEntity admin = createUser("automation-native-stale-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Native Stale");
        Integer boxId = createBox(token, roomId, "Box Native Stale");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Integer sensorId = seedNativeSensor(
                admin.getId(),
                "native-stale-device",
                now.minusMinutes(21),
                now,
                "OK"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"AIR_TEMPERATURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d}
                        ]}
                        """.formatted(sensorId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"))
                .body("rooms[0].boxes[0].resources[0].connection_message", equalTo("нет связи"));
    }

    @Test
    void equipmentKeepsResourceOfflineThreshold() {
        UserEntity admin = createUser("automation-equipment-stale-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Equipment Stale");
        Integer boxId = createBox(token, roomId, "Box Equipment Stale");
        jdbcTemplate.update(
                "UPDATE zigbee_device_snapshots SET last_state_at=? WHERE friendly_name='smartplug1'",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"LIGHT_SWITCH",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df3",
                            "zigbee_property":"state",
                            "command_property":"state",
                            "on_value":"ON",
                            "off_value":"OFF"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"));
    }

    @Test
    void zigbeeOfflineWarnsImmediatelyWithFreshState() {
        UserEntity admin = createUser("automation-zigbee-offline-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Zigbee Offline");
        Integer boxId = createBox(token, roomId, "Box Zigbee Offline");
        seedZigbeeAvailability("temp_sensor1", "offline", LocalDateTime.now(ZoneOffset.UTC));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {
                            "role":"AIR_TEMPERATURE_SENSOR",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c138ccd6b42d0c",
                            "zigbee_property":"temperature"
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"));
    }

    @Test
    void nativeSensorConnectionWarnsWhenSensorStatusIsNotOk() {
        UserEntity admin = createUser("automation-native-status-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Native Status");
        Integer boxId = createBox(token, roomId, "Box Native Status");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Integer disconnectedSensorId = seedNativeSensor(
                admin.getId(),
                "native-disconnected-device",
                now,
                now,
                "DISCONNECTED"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"AIR_TEMPERATURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d}
                        ]}
                        """.formatted(disconnectedSensorId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"))
                .body("rooms[0].boxes[0].resources[0].connection_message", equalTo("нет связи"));

        Integer errorSensorId = seedNativeSensor(
                admin.getId(),
                "native-error-device",
                now,
                now,
                "ERROR"
        );

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"AIR_TEMPERATURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d}
                        ]}
                        """.formatted(errorSensorId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].resources[0].connection_status", equalTo("warning"))
                .body("rooms[0].boxes[0].resources[0].connection_message", equalTo("нет связи"));
    }

    @Test
    void untilDrainScenarioRequiresLeakSensor() {
        UserEntity admin = createUser("automation-until-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Until");
        Integer boxId = createBox(token, roomId, "Box Until");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[
                          {
                            "scenario_type":"WATERING",
                            "enabled":true,
                            "config":{"stop_mode":"until_drain","max_run_minutes":5}
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/scenarios")
                .then()
                .statusCode(400)
                .body("detail", equalTo("dlya until_drain nuzhen LEAK_SENSOR"));
    }

    @Test
    void legacyLightAutomationKeepsPublishingToExistingBaseTopic() {
        UserEntity admin = createUser("automation-legacy-light@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Legacy Room");
        Integer boxId = createBox(token, roomId, "Legacy Box");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[{
                          "role":"LIGHT_SWITCH",
                          "source_type":"ZIGBEE_DEVICE",
                          "zigbee_ieee_address":"0xa4c13895af2c1df3",
                          "command_property":"state"
                        }]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"LIGHT_SCHEDULE",
                          "enabled":true,
                          "config":{"start_time":"00:00","end_time":"00:00","days":[1,2,3,4,5,6,7]}
                        }]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/scenarios")
                .then()
                .statusCode(200);

        automationFacade.evaluateAll();

        assertThat(testPublisher.published()).anySatisfy(message -> {
            assertThat(message.topic()).isEqualTo("zigbee2growerhub/smartplug1/set");
            assertThat(message.payload()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) message.payload()).get("state")).isEqualTo("ON");
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result FROM automation_action_log WHERE scope_type='BOX' AND scope_id=? "
                        + "AND scenario_type='LIGHT_SCHEDULE' ORDER BY id DESC LIMIT 1",
                String.class,
                boxId
        )).isEqualTo("published");
    }

    @Test
    void untilDrainWateringUsesPersistentSessionAndStopsOnLeak() {
        UserEntity admin = createUser("automation-runtime-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Runtime");
        Integer boxId = createBox(token, roomId, "Box Runtime");
        NativeWateringResources nativeResources = seedNativeWateringResources(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"items":[
                          {"plant_id":%d,"rate_ml_per_hour":2000}
                        ]}
                        """.formatted(nativeResources.plantId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"SOIL_MOISTURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d},
                          {"role":"WATER_PUMP","source_type":"NATIVE_PUMP","native_pump_id":%d},
                          {
                            "role":"LEAK_SENSOR",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df4",
                            "zigbee_property":"water_leak"
                          }
                        ]}
                        """.formatted(nativeResources.soilSensorId(), nativeResources.pumpId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200);

        seedLeakState(LocalDateTime.now(ZoneOffset.UTC).minusDays(2), false);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[
                          {
                            "scenario_type":"WATERING",
                            "enabled":true,
                            "config":{
                              "soil_threshold_percent":40,
                              "max_interval_hours":48,
                              "stop_mode":"until_drain",
                              "max_run_minutes":5,
                              "min_interval_hours":0,
                              "daily_max_seconds":1200
                            }
                          }
                        ]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/scenarios")
                .then()
                .statusCode(200);

        automationFacade.evaluateAll();

        String phase = jdbcTemplate.queryForObject(
                "SELECT phase FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(phase).isEqualTo("running");
        String source = jdbcTemplate.queryForObject(
                "SELECT source FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(source).isEqualTo("automation");

        seedLeakState(LocalDateTime.now(ZoneOffset.UTC), true);
        automationFacade.evaluateActiveWateringSessions();

        String phaseAfterLeak = jdbcTemplate.queryForObject(
                "SELECT phase FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(phaseAfterLeak).isEqualTo("stopping");
        String stopReason = jdbcTemplate.queryForObject(
                "SELECT completion_reason FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(stopReason).isEqualTo("leak");
    }

    @Test
    void boxPlantsAcceptItemsWithRateAndLegacyPlantIds() {
        UserEntity admin = createUser("automation-plants-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Plants");
        Integer boxId = createBox(token, roomId, "Box Plants");
        Integer firstPlantId = seedPlant(admin.getId(), "Plant With Rate");
        Integer secondPlantId = seedPlant(admin.getId(), "Plant Legacy");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"items":[
                          {"plant_id":%d,"rate_ml_per_hour":2400},
                          {"plant_id":%d,"rate_ml_per_hour":null}
                        ]}
                        """.formatted(firstPlantId, secondPlantId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].plants.find { it.id == %d }.rate_ml_per_hour".formatted(firstPlantId), equalTo(2400))
                .body("rooms[0].boxes[0].plants.find { it.id == %d }.name".formatted(secondPlantId), equalTo("Plant Legacy"));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"plant_ids\":[" + secondPlantId + "]}")
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200)
                .body("rooms[0].boxes[0].plants", hasSize(1))
                .body("rooms[0].boxes[0].plants[0].id", equalTo(secondPlantId));

        Integer legacyRate = jdbcTemplate.queryForObject(
                "SELECT rate_ml_per_hour FROM automation_box_plants WHERE box_id=? AND plant_id=?",
                Integer.class,
                boxId,
                secondPlantId
        );
        assertThat(legacyRate).isNull();
    }

    @Test
    void manualWateringOverviewStartPulseLeakAndStatisticsUseAutomationTopology() {
        UserEntity admin = createUser("manual-watering-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Manual");
        Integer boxId = createBox(token, roomId, "Box Manual");
        NativeWateringResources nativeResources = seedNativeWateringResources(admin.getId());

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"items":[
                          {"plant_id":%d,"rate_ml_per_hour":2000}
                        ]}
                        """.formatted(nativeResources.plantId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"WATER_PUMP","source_type":"NATIVE_PUMP","native_pump_id":%d},
                          {
                            "role":"LEAK_SENSOR",
                            "source_type":"ZIGBEE_DEVICE",
                            "zigbee_ieee_address":"0xa4c13895af2c1df4",
                            "zigbee_property":"water_leak"
                          }
                        ]}
                        """.formatted(nativeResources.pumpId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/manual-watering")
                .then()
                .statusCode(200)
                .body("defaults.timed_duration_s", equalTo(300))
                .body("defaults.until_leak_max_active_duration_s", equalTo(1800))
                .body("defaults.pulse_run_s", equalTo(180))
                .body("defaults.pulse_pause_s", equalTo(300))
                .body("pumps.find { it.id == %d }.capabilities.can_start".formatted(nativeResources.pumpId()), equalTo(true))
                .body("pumps.find { it.id == %d }.capabilities.until_leak".formatted(nativeResources.pumpId()), equalTo(true))
                .body("pumps.find { it.id == %d }.boxes[0].id".formatted(nativeResources.pumpId()), equalTo(boxId))
                .body("pumps.find { it.id == %d }.boxes[0].enabled".formatted(nativeResources.pumpId()), equalTo(true))
                .body("pumps.find { it.id == %d }.boxes[0].plants[0].id".formatted(nativeResources.pumpId()), equalTo(nativeResources.plantId()))
                .body("pumps.find { it.id == %d }.boxes[0].plants[0].rate_ml_per_hour".formatted(nativeResources.pumpId()), equalTo(2000))
                .body("pumps.find { it.id == %d }.boxes[0].leak_sensors[0].available".formatted(nativeResources.pumpId()), equalTo(true));

        seedZigbeeAvailability("leak1", "offline", LocalDateTime.now(ZoneOffset.UTC));
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/manual-watering")
                .then()
                .statusCode(200)
                .body("pumps.find { it.id == %d }.capabilities.until_leak".formatted(nativeResources.pumpId()), equalTo(false))
                .body("pumps.find { it.id == %d }.boxes[0].leak_sensors[0].available".formatted(nativeResources.pumpId()), equalTo(false));

        jdbcTemplate.update("UPDATE zigbee_device_snapshots SET availability=NULL WHERE friendly_name='leak1'");
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/manual-watering")
                .then()
                .statusCode(200)
                .body("pumps.find { it.id == %d }.capabilities.until_leak".formatted(nativeResources.pumpId()), equalTo(false));
        seedZigbeeAvailability("leak1", "online", LocalDateTime.now(ZoneOffset.UTC));

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                          "mode":"timed",
                          "duration_s":20,
                          "pulse_enabled":true,
                          "pulse_run_s":1,
                          "pulse_pause_s":30
                        }
                        """)
                .when()
                .post("/api/admin/manual-watering/pumps/" + nativeResources.pumpId() + "/start")
                .then()
                .statusCode(200)
                .body("source", equalTo("admin_manual"))
                .body("mode", equalTo("timed"))
                .body("pulse_enabled", equalTo(true))
                .body("boxes[0].box_id", equalTo(boxId))
                .body("boxes[0].plants[0].plant_id", equalTo(nativeResources.plantId()))
                .body("boxes[0].plants[0].rate_ml_per_hour", equalTo(2000));

        jdbcTemplate.update(
                "UPDATE pump_watering_sessions SET phase_started_at=? WHERE pump_id=? AND active_device_key IS NOT NULL",
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2),
                nativeResources.pumpId()
        );
        automationFacade.evaluateActiveWateringSessions();
        String pulseStopPhase = jdbcTemplate.queryForObject(
                "SELECT phase FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(pulseStopPhase).isEqualTo("stopping");

        reportPumpIdle("native-water-1");
        automationFacade.evaluateActiveWateringSessions();
        String pausePhase = jdbcTemplate.queryForObject(
                "SELECT phase FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(pausePhase).isEqualTo("pause");

        seedLeakState(LocalDateTime.now(ZoneOffset.UTC), true);
        automationFacade.evaluateActiveWateringSessions();
        String leakStopPhase = jdbcTemplate.queryForObject(
                "SELECT phase FROM pump_watering_sessions WHERE pump_id=? ORDER BY id DESC LIMIT 1",
                String.class,
                nativeResources.pumpId()
        );
        assertThat(leakStopPhase).isEqualTo("completed");
        reportPumpIdle("native-water-1");
        automationFacade.evaluateActiveWateringSessions();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("range", "day")
                .when()
                .get("/api/admin/manual-watering/boxes/" + boxId + "/statistics")
                .then()
                .statusCode(200)
                .body("session_count", equalTo(1))
                .body("active_duration_s", equalTo(1))
                .body("reason_counts.leak", equalTo(1))
                .body("sessions[0].completion_reason", equalTo("leak"))
                .body("sessions[0].boxes[0].plants[0].duration_s", equalTo(1));
    }

    @Test
    void existingUserStartEndpointUsesAutomationBoxSnapshots() {
        UserEntity owner = createUser("watering-owner@example.com", "user");
        UserEntity admin = createUser("watering-config-admin@example.com", "admin");
        String adminToken = buildToken(admin.getId());
        Integer roomId = createRoom(adminToken, "Room User Start");
        Integer boxId = createBox(adminToken, roomId, "Box User Start");
        NativeWateringResources nativeResources = seedNativeWateringResources(owner.getId());

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("""
                        {"items":[
                          {"plant_id":%d,"rate_ml_per_hour":1800}
                        ]}
                        """.formatted(nativeResources.plantId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + buildToken(owner.getId()))
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/pumps/" + nativeResources.pumpId() + "/watering/start")
                .then()
                .statusCode(400)
                .body("detail", equalTo("ukazhite water_volume_l ili duration_s dlya starta poliva"));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"WATER_PUMP","source_type":"NATIVE_PUMP","native_pump_id":%d}
                        ]}
                        """.formatted(nativeResources.pumpId()))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + buildToken(owner.getId()))
                .contentType("application/json")
                .body("{\"duration_s\":60,\"water_volume_l\":0.3}")
                .when()
                .post("/api/pumps/" + nativeResources.pumpId() + "/watering/start")
                .then()
                .statusCode(200);

        Map<String, Object> snapshot = jdbcTemplate.queryForMap(
                """
                        SELECT sessions.source, boxes.box_id, plants.plant_id, plants.rate_ml_per_hour
                        FROM pump_watering_sessions sessions
                        JOIN pump_watering_session_boxes boxes ON boxes.session_id=sessions.id
                        JOIN pump_watering_session_plants plants ON plants.session_box_id=boxes.id
                        WHERE sessions.pump_id=?
                        """,
                nativeResources.pumpId()
        );
        assertThat(snapshot.get("source")).isEqualTo("user_manual");
        assertThat(((Number) snapshot.get("box_id")).intValue()).isEqualTo(boxId);
        assertThat(((Number) snapshot.get("plant_id")).intValue()).isEqualTo(nativeResources.plantId());
        assertThat(((Number) snapshot.get("rate_ml_per_hour")).intValue()).isEqualTo(1800);
    }

    @Test
    void wateringWorkerContinuesAfterOneSessionFails() {
        PumpSessionData.Probe malformed = new PumpSessionData.Probe(
                1001L,
                1001,
                "missing-water-device-1",
                PumpSessionData.MODE_TIMED,
                PumpSessionData.PHASE_RUNNING,
                null
        );
        PumpSessionData.Probe failed = new PumpSessionData.Probe(
                1002L,
                1002,
                "missing-water-device-2",
                PumpSessionData.MODE_TIMED,
                PumpSessionData.PHASE_RUNNING,
                List.of()
        );
        PumpSessionData.Probe next = new PumpSessionData.Probe(
                1003L,
                1003,
                "missing-water-device-3",
                PumpSessionData.MODE_TIMED,
                PumpSessionData.PHASE_RUNNING,
                List.of()
        );
        doReturn(List.of(malformed, failed, next)).when(pumpFacade).listActiveSessionProbes();
        doThrow(new IllegalStateException("test session failure"))
                .when(pumpFacade)
                .advanceSession(eq(failed.sessionId()), any(), any());
        doReturn(null)
                .when(pumpFacade)
                .advanceSession(eq(next.sessionId()), any(), any());

        automationFacade.evaluateActiveWateringSessions();

        verify(pumpFacade, never()).advanceSession(eq(malformed.sessionId()), any(), any());
        verify(pumpFacade).advanceSession(eq(failed.sessionId()), any(), any());
        verify(pumpFacade).advanceSession(eq(next.sessionId()), any(), any());
    }

    @Test
    void automaticSessionSurvivesRollbackOfLaterBoxEvaluation() {
        UserEntity admin = createUser("automation-session-isolation@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Session Isolation");
        Integer boxId = createBox(token, roomId, "Box Session Isolation");
        NativeWateringResources nativeResources = seedNativeWateringResources(admin.getId());
        configureTimedWateringBox(
                token,
                boxId,
                nativeResources.plantId(),
                nativeResources.soilSensorId(),
                nativeResources.pumpId()
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            automationFacade.evaluateAll();
            throw new IllegalStateException("test later evaluation failure");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("test later evaluation failure");

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT source, phase, active_device_key FROM pump_watering_sessions WHERE pump_id=?",
                nativeResources.pumpId()
        );
        assertThat(persisted.get("source")).isEqualTo("automation");
        assertThat(persisted.get("phase")).isEqualTo("running");
        assertThat(persisted.get("active_device_key")).isEqualTo("native-water-1");
    }

    private void configureTimedWateringBox(
            String token,
            Integer boxId,
            Integer plantId,
            Integer soilSensorId,
            Integer pumpId
    ) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"items\":[{\"plant_id\":" + plantId + ",\"rate_ml_per_hour\":2000}]}")
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/plants")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"resources":[
                          {"role":"SOIL_MOISTURE_SENSOR","source_type":"NATIVE_SENSOR","native_sensor_id":%d},
                          {"role":"WATER_PUMP","source_type":"NATIVE_PUMP","native_pump_id":%d}
                        ]}
                        """.formatted(soilSensorId, pumpId))
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/resources")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"scenarios":[{
                          "scenario_type":"WATERING",
                          "enabled":true,
                          "config":{
                            "soil_threshold_percent":40,
                            "max_interval_hours":48,
                            "stop_mode":"fixed_duration",
                            "run_seconds":30,
                            "min_interval_hours":0,
                            "daily_max_seconds":1200
                          }
                        }]}
                        """)
                .when()
                .put("/api/admin/automation/boxes/" + boxId + "/scenarios")
                .then()
                .statusCode(200);
    }

    private void reportPumpIdle(String deviceKey) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        deviceFacade.handleState(
                deviceKey,
                new DeviceShadowState(
                        new DeviceShadowState.ManualWateringState(
                                "idle",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new DeviceShadowState.RelayState("off"),
                        null
                ),
                now
        );
    }

    private Integer createRoom(String token, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post("/api/admin/automation/rooms")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].id");
    }

    private Integer createBox(String token, Integer roomId, String name) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post("/api/admin/automation/rooms/" + roomId + "/boxes")
                .then()
                .statusCode(200)
                .extract()
                .path("rooms[0].boxes[0].id");
    }

    private void seedZigbee() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.BRIDGE_DEVICES,
                "zigbee2growerhub/bridge/devices",
                "bridge/devices",
                null,
                "[]",
                List.of(smartPlugBridgeDevice(), temperatureSensorBridgeDevice(), leakSensorBridgeDevice()),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/smartplug1",
                "smartplug1",
                "smartplug1",
                "{\"state\":\"OFF\",\"power\":0}",
                Map.of("state", "OFF", "power", 0),
                now
        ));
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/temp_sensor1",
                "temp_sensor1",
                "temp_sensor1",
                "{\"temperature\":24.5}",
                Map.of("temperature", 24.5),
                now
        ));
        seedLeakState(now, false);
        seedZigbeeAvailability("leak1", "online", now);
    }

    private void seedLegacyCoordinator() {
        UserEntity owner = createUser("automation-legacy-owner@example.com", "admin");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        coordinatorRepository.save(ZigbeeCoordinatorEntity.create(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                owner.getId(),
                "Legacy coordinator",
                "legacy",
                "zigbee2growerhub",
                now
        ));
    }

    private void seedLeakState(LocalDateTime receivedAt, boolean leak) {
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/leak1",
                "leak1",
                "leak1",
                "{\"water_leak\":" + leak + "}",
                Map.of("water_leak", leak),
                receivedAt
        ));
    }

    private void seedZigbeeAvailability(String friendlyName, String availability, LocalDateTime receivedAt) {
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_AVAILABILITY,
                "zigbee2growerhub/" + friendlyName + "/availability",
                friendlyName + "/availability",
                friendlyName,
                "{\"state\":\"" + availability + "\"}",
                Map.of("state", availability),
                receivedAt
        ));
    }

    private Map<String, Object> smartPlugBridgeDevice() {
        return Map.of(
                "friendly_name", "smartplug1",
                "ieee_address", "0xa4c13895af2c1df3",
                "type", "Router",
                "supported", true,
                "definition", Map.of(
                        "model", "TS011F_plug_1_1",
                        "vendor", "Zbeacon",
                        "exposes", List.of(
                                Map.of("type", "switch", "features", List.of(
                                        Map.of(
                                                "type", "binary",
                                                "name", "state",
                                                "property", "state",
                                                "access", 7,
                                                "value_on", "ON",
                                                "value_off", "OFF",
                                                "label", "State"
                                        )
                                )),
                                Map.of(
                                        "type", "numeric",
                                        "name", "power",
                                        "property", "power",
                                        "access", 1,
                                        "unit", "W",
                                        "label", "Power"
                                )
                        )
                )
        );
    }

    private Map<String, Object> temperatureSensorBridgeDevice() {
        return Map.of(
                "friendly_name", "temp_sensor1",
                "ieee_address", "0xa4c138ccd6b42d0c",
                "type", "EndDevice",
                "supported", true,
                "definition", Map.of(
                        "model", "TS0601_temperature_humidity_sensor",
                        "vendor", "Tuya",
                        "exposes", List.of(
                                Map.of(
                                        "type", "numeric",
                                        "name", "temperature",
                                        "property", "temperature",
                                        "access", 1,
                                        "unit", "C",
                                        "label", "Temperature"
                                )
                        )
                )
        );
    }

    private Map<String, Object> leakSensorBridgeDevice() {
        return Map.of(
                "friendly_name", "leak1",
                "ieee_address", "0xa4c13895af2c1df4",
                "type", "EndDevice",
                "supported", true,
                "definition", Map.of(
                        "model", "TS0207_water_leak",
                        "vendor", "Tuya",
                        "exposes", List.of(
                                Map.of(
                                        "type", "binary",
                                        "name", "water_leak",
                                        "property", "water_leak",
                                        "access", 1,
                                        "value_on", true,
                                        "value_off", false,
                                        "label", "Water leak"
                                )
                        )
                )
        );
    }

    private NativeWateringResources seedNativeWateringResources(Integer ownerId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO devices (device_id, name, user_id, last_seen) VALUES (?, ?, ?, ?)",
                "native-water-1",
                "Native water",
                ownerId,
                now
        );
        Integer deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM devices WHERE device_id=?",
                Integer.class,
                "native-water-1"
        );
        jdbcTemplate.update(
                "INSERT INTO plants (user_id, name, planted_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ownerId,
                "Plant Runtime",
                now,
                now,
                now
        );
        Integer plantId = jdbcTemplate.queryForObject(
                "SELECT id FROM plants WHERE name=?",
                Integer.class,
                "Plant Runtime"
        );
        jdbcTemplate.update(
                "INSERT INTO sensors (device_id, type, channel, label, detected, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                deviceId,
                "SOIL_MOISTURE",
                1,
                "Soil",
                true,
                "OK",
                now,
                now
        );
        Integer soilSensorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sensors WHERE device_id=? AND type='SOIL_MOISTURE' AND channel=1",
                Integer.class,
                deviceId
        );
        jdbcTemplate.update(
                "INSERT INTO sensor_readings (sensor_id, ts, value_numeric, created_at) VALUES (?, ?, ?, ?)",
                soilSensorId,
                now,
                20.0,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO pumps (device_id, channel, label, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                deviceId,
                1,
                "Pump",
                now,
                now
        );
        Integer pumpId = jdbcTemplate.queryForObject(
                "SELECT id FROM pumps WHERE device_id=? AND channel=1",
                Integer.class,
                deviceId
        );
        jdbcTemplate.update(
                "INSERT INTO pump_plant_bindings (pump_id, plant_id, rate_ml_per_hour) VALUES (?, ?, ?)",
                pumpId,
                plantId,
                2000
        );
        return new NativeWateringResources(soilSensorId, pumpId, plantId);
    }

    private Integer seedPlant(Integer ownerId, String name) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO plants (user_id, name, planted_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                ownerId,
                name,
                now,
                now,
                now
        );
        return jdbcTemplate.queryForObject("SELECT id FROM plants WHERE name=?", Integer.class, name);
    }

    private Integer seedNativeSensor(
            Integer ownerId,
            String deviceKey,
            LocalDateTime deviceLastSeen,
            LocalDateTime readingTs,
            String status
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO devices (device_id, name, user_id, last_seen) VALUES (?, ?, ?, ?)",
                deviceKey,
                "Native sensor " + deviceKey,
                ownerId,
                deviceLastSeen
        );
        Integer deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM devices WHERE device_id=?",
                Integer.class,
                deviceKey
        );
        jdbcTemplate.update(
                "INSERT INTO sensors (device_id, type, channel, label, detected, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                deviceId,
                "AIR_TEMPERATURE",
                0,
                "Air",
                true,
                status,
                now,
                now
        );
        Integer sensorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sensors WHERE device_id=? AND type='AIR_TEMPERATURE' AND channel=0",
                Integer.class,
                deviceId
        );
        jdbcTemplate.update(
                "INSERT INTO sensor_readings (sensor_id, ts, value_numeric, created_at) VALUES (?, ?, ?, ?)",
                sensorId,
                readingTs,
                24.0,
                now
        );
        return sensorId;
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
                .setExpiration(Date.from(java.time.Instant.now().plusSeconds(3600)))
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
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM pump_watering_session_leaks");
        jdbcTemplate.update("DELETE FROM pump_watering_session_plants");
        jdbcTemplate.update("DELETE FROM pump_watering_session_boxes");
        jdbcTemplate.update("DELETE FROM pump_watering_sessions");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM device_service_events");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM plant_groups");
        jdbcTemplate.update("DELETE FROM zigbee_command_response_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_bridge_snapshots");
        jdbcTemplate.update("DELETE FROM zigbee_coordinators");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    private record NativeWateringResources(Integer soilSensorId, Integer pumpId, Integer plantId) {
    }

    @TestConfiguration
    static class TestPublisherConfig {
        @Bean
        TestPublisher testPublisher() {
            return new TestPublisher();
        }
    }

    static class TestPublisher implements MqttPublisher {
        private final List<PublishedMessage> published = new ArrayList<>();

        @Override
        public void publishCmd(String deviceId, Object cmd) {
        }

        @Override
        public void publishJson(String topic, Object payload, int qos, boolean retained) {
            published.add(new PublishedMessage(topic, payload));
        }

        List<PublishedMessage> published() {
            return List.copyOf(published);
        }

        void clear() {
            published.clear();
        }
    }

    private record PublishedMessage(String topic, Object payload) {
    }
}
