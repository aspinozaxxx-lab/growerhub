package ru.growerhub.backend.device;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;
import ru.growerhub.backend.mqtt.model.DeviceState;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private SensorDataRepository sensorDataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sensor_data");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM devices");
    }

    @Test
    void handleStatePersistsSensorHistory() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
        DeviceState state = new DeviceState(null, null, null, null, null, 10.0, 20.0, 30.0);

        deviceService.handleState("device-1", state, now);

        SensorDataEntity record = sensorDataRepository.findAll().stream().findFirst().orElse(null);
        Assertions.assertNotNull(record);
        Assertions.assertEquals("device-1", record.getDeviceId());
        Assertions.assertEquals(now, record.getTimestamp());
        Assertions.assertEquals(10.0, record.getSoilMoisture());
        Assertions.assertEquals(20.0, record.getAirTemperature());
        Assertions.assertEquals(30.0, record.getAirHumidity());
    }

    @Test
    void handleStateSkipsSensorHistoryWhenEmpty() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 1, 12, 0);
        DeviceState state = new DeviceState(null, null, null, null, null, null, null, null);

        deviceService.handleState("device-2", state, now);

        Assertions.assertEquals(0, sensorDataRepository.count());
    }
}
