package ru.growerhub.backend.device;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorReadingRepository;
import ru.growerhub.backend.sensor.SensorRepository;
import ru.growerhub.backend.sensor.SensorType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceIngestionServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DeviceIngestionService deviceIngestionService;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM devices");
    }

    @Test
    void handleStatePersistsSensorReadings() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
        DeviceState state = new DeviceState(null, null, 10.0, 20.0, 30.0, null, null, null, null, null);

        deviceIngestionService.handleState("device-1", state, now);

        Assertions.assertEquals(3, sensorRepository.count());
        Assertions.assertEquals(3, sensorReadingRepository.count());

        List<SensorEntity> sensors = sensorRepository.findAll();
        Assertions.assertTrue(sensors.stream().anyMatch(sensor -> sensor.getType() == SensorType.SOIL_MOISTURE));
        Assertions.assertTrue(sensors.stream().anyMatch(sensor -> sensor.getType() == SensorType.AIR_TEMPERATURE));
        Assertions.assertTrue(sensors.stream().anyMatch(sensor -> sensor.getType() == SensorType.AIR_HUMIDITY));
    }

    @Test
    void handleStateSkipsSensorReadingsWhenEmpty() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
        DeviceState state = new DeviceState(null, null, null, null, null, null, null, null, null, null);

        deviceIngestionService.handleState("device-2", state, now);

        Assertions.assertEquals(0, sensorReadingRepository.count());
    }

    @Test
    void handleStatePersistsV2SensorReadings() {
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 12, 0);
        DeviceState.AirState air = new DeviceState.AirState(true, 21.5, 50.0);
        DeviceState.SoilPort port0 = new DeviceState.SoilPort(0, true, 40);
        DeviceState.SoilPort port1 = new DeviceState.SoilPort(1, false, 70);
        DeviceState.SoilState soil = new DeviceState.SoilState(List.of(port0, port1));
        DeviceState state = new DeviceState(null, null, null, null, null, air, soil, null, null, null);

        deviceIngestionService.handleState("device-3", state, now);

        Assertions.assertEquals(4, sensorRepository.count());
        Assertions.assertEquals(4, sensorReadingRepository.count());
    }
}
