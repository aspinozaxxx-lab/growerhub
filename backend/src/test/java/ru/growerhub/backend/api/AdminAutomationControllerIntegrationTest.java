package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MQTT_HOST="
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
    private AutomationFacade automationFacade;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        clearDatabase();
        seedZigbee();
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
                now,
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
                now.minusMinutes(10),
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
    void untilDrainWateringStopsOnLeakAndClearsRuntime() {
        UserEntity admin = createUser("automation-runtime-admin@example.com", "admin");
        String token = buildToken(admin.getId());
        Integer roomId = createRoom(token, "Room Runtime");
        Integer boxId = createBox(token, roomId, "Box Runtime");
        NativeWateringResources nativeResources = seedNativeWateringResources(admin.getId());

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

        String runtime = jdbcTemplate.queryForObject(
                "SELECT runtime_json FROM automation_scenario_states WHERE scope_type='BOX' AND scope_id=? AND scenario_type='WATERING'",
                String.class,
                boxId
        );
        assertThat(runtime).contains("\"watering_session_active\":true");

        seedLeakState(LocalDateTime.now(ZoneOffset.UTC), true);
        automationFacade.evaluateActiveWateringSessions();

        String runtimeAfterStop = jdbcTemplate.queryForObject(
                "SELECT runtime_json FROM automation_scenario_states WHERE scope_type='BOX' AND scope_id=? AND scenario_type='WATERING'",
                String.class,
                boxId
        );
        assertThat(runtimeAfterStop).doesNotContain("watering_session_active");
        String stopReason = jdbcTemplate.queryForObject(
                "SELECT reason FROM automation_action_log WHERE scope_type='BOX' AND scope_id=? AND action='PUMP_STOP' ORDER BY id DESC LIMIT 1",
                String.class,
                boxId
        );
        assertThat(stopReason).isEqualTo("drenazh");
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
        return new NativeWateringResources(soilSensorId, pumpId);
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
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    private record NativeWateringResources(Integer soilSensorId, Integer pumpId) {
    }

    @TestConfiguration
    static class TestPublisherConfig {
        @Bean
        TestPublisher testPublisher() {
            return new TestPublisher();
        }
    }

    static class TestPublisher implements MqttPublisher {
        @Override
        public void publishCmd(String deviceId, Object cmd) {
        }

        @Override
        public void publishJson(String topic, Object payload, int qos, boolean retained) {
        }
    }
}
