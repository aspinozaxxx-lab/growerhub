package ru.growerhub.backend.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.engine.DeviceIngestionService;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.device.jpa.MqttAckEntity;
import ru.growerhub.backend.device.jpa.MqttAckRepository;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingRepository;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.sensor.SensorType;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST=", "ACK_TTL_SECONDS=60"}
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MqttMessageHandlingIntegrationTest.InjectorConfig.class)
class MqttMessageHandlingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ManualInjectorSubscriber injectorSubscriber;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceStateLastRepository deviceStateLastRepository;

    @Autowired
    private MqttAckRepository mqttAckRepository;

    @Autowired
    private AckStore ackStore;

    @Autowired
    private DeviceShadowStore shadowStore;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorPlantBindingRepository sensorPlantBindingRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantMetricSampleRepository plantMetricSampleRepository;

    @SpyBean
    private DeviceIngestionService deviceIngestionService;

    @BeforeEach
    void setUp() {
        clearDatabase();
        ackStore.clear();
        shadowStore.clear();
    }

    @Test
    void handlesStateAndAckMessages() {
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("mqtt-1");
        deviceRepository.save(device);

        String stateJson = """
                {"manual_watering":{"status":"running","duration_s":30,"started_at":"2025-01-01T00:00:00Z","remaining_s":30,"correlation_id":"corr-1"}}
                """;
        injectorSubscriber.injectState("gh/dev/mqtt-1/state", stateJson.getBytes(StandardCharsets.UTF_8));

        DeviceStateLastEntity state = deviceStateLastRepository.findByDeviceId("mqtt-1").orElse(null);
        Assertions.assertNotNull(state);
        Assertions.assertTrue(state.getStateJson().contains("manual_watering"));
        Assertions.assertNotNull(shadowStore.getLastState("mqtt-1"));

        DeviceEntity afterState = deviceRepository.findByDeviceId("mqtt-1").orElse(null);
        Assertions.assertNotNull(afterState);
        Assertions.assertNotNull(afterState.getLastSeen());

        LocalDateTime prevUpdated = state.getUpdatedAt();

        String ackJson = """
                {"correlation_id":"corr-1","result":"accepted","status":"running","reason":"ok"}
                """;
        injectorSubscriber.injectAck("gh/dev/mqtt-1/state/ack", ackJson.getBytes(StandardCharsets.UTF_8));

        Assertions.assertNotNull(ackStore.get("corr-1"));

        MqttAckEntity ackRecord = mqttAckRepository.findByCorrelationId("corr-1").orElse(null);
        Assertions.assertNotNull(ackRecord);
        Assertions.assertEquals("mqtt-1", ackRecord.getDeviceId());
        Assertions.assertEquals("accepted", ackRecord.getResult());
        Assertions.assertNotNull(ackRecord.getExpiresAt());
        Assertions.assertTrue(ackRecord.getExpiresAt().isAfter(ackRecord.getReceivedAt()));

        DeviceStateLastEntity touched = deviceStateLastRepository.findByDeviceId("mqtt-1").orElse(null);
        Assertions.assertNotNull(touched);
        Assertions.assertTrue(touched.getUpdatedAt().isAfter(prevUpdated) || touched.getUpdatedAt().isEqual(prevUpdated));

        DeviceEntity updatedDevice = deviceRepository.findByDeviceId("mqtt-1").orElse(null);
        Assertions.assertNotNull(updatedDevice);
        Assertions.assertNotNull(updatedDevice.getLastSeen());
        Assertions.assertTrue(updatedDevice.getLastSeen().isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }

    @Test
    void autoRegistersDeviceOnStateMessage() {
        String deviceId = "mqtt-new";
        String stateJson = """
                {"manual_watering":{"status":"running","duration_s":30,"started_at":"2025-01-01T00:00:00Z","remaining_s":30,"correlation_id":"corr-new"}}
                """;
        injectorSubscriber.injectState("gh/dev/" + deviceId + "/state", stateJson.getBytes(StandardCharsets.UTF_8));

        verify(deviceIngestionService).handleState(eq(deviceId), any(DeviceShadowState.class), any(LocalDateTime.class));

        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        Assertions.assertNotNull(device);
        Assertions.assertEquals("Watering Device " + deviceId, device.getName());
        Assertions.assertNotNull(device.getLastSeen());
        Assertions.assertEquals(40.0, device.getTargetMoisture());
        Assertions.assertEquals(30, device.getWateringDuration());
        Assertions.assertEquals(300, device.getWateringTimeout());
        Assertions.assertEquals(false, device.getUpdateAvailable());
        Assertions.assertNull(device.getUser());

        DeviceStateLastEntity state = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        Assertions.assertNotNull(state);
        Assertions.assertNotNull(shadowStore.getLastState(deviceId));
    }

    @Test
    void persistsSensorAndPlantHistoryFromStateMessage() {
        String deviceId = "mqtt-sensor";
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId(deviceId);
        deviceRepository.save(device);

        PlantEntity plant = PlantEntity.create();
        plant.setName("Sensor Plant");
        plant.setPlantedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantRepository.save(plant);

        SensorEntity airTemp = createSensor(device, SensorType.AIR_TEMPERATURE, 0);
        SensorEntity airHum = createSensor(device, SensorType.AIR_HUMIDITY, 0);
        SensorEntity soil0 = createSensor(device, SensorType.SOIL_MOISTURE, 0);

        bindSensorToPlant(airTemp, plant);
        bindSensorToPlant(airHum, plant);
        bindSensorToPlant(soil0, plant);

        String stateJson = """
                {"air":{"available":true,"temperature":22.0,"humidity":55.5},"soil":{"ports":[{"port":0,"detected":true,"percent":12},{"port":1,"detected":false}]},"pump":{"status":"on"},"light":{"status":"off"}}
                """;
        injectorSubscriber.injectState("gh/dev/" + deviceId + "/state", stateJson.getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(3, sensorReadingRepository.count());
        Assertions.assertTrue(plantMetricSampleRepository.count() >= 3);
    }

    private SensorEntity createSensor(DeviceEntity device, SensorType type, int channel) {
        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(device.getId());
        sensor.setType(type);
        sensor.setChannel(channel);
        sensor.setDetected(true);
        sensor.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        sensor.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return sensorRepository.save(sensor);
    }

    private void bindSensorToPlant(SensorEntity sensor, PlantEntity plant) {
        SensorPlantBindingEntity binding = SensorPlantBindingEntity.create();
        binding.setSensor(sensor);
        binding.setPlantId(plant.getId());
        sensorPlantBindingRepository.save(binding);
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM sensor_plant_bindings");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM mqtt_ack");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM plants");
    }

    @TestConfiguration
    static class InjectorConfig {
        @Bean
        ManualInjectorSubscriber manualInjectorSubscriber(MqttMessageHandler handler) {
            return new ManualInjectorSubscriber(handler);
        }
    }
}





