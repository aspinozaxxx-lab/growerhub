package ru.growerhub.backend.maintenance;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.maintenance.contract.HistoryRetentionResult;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.pump.jpa.PumpStateReadingEntity;
import ru.growerhub.backend.pump.jpa.PumpStateReadingRepository;
import ru.growerhub.backend.sensor.contract.SensorType;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventRepository;

@SpringBootTest(properties = {
        "history.retention.enabled=true",
        "history.retention.rawDays=30",
        "history.retention.startupDelayMs=999999999"
})
class HistoryCompactionIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MaintenanceFacade maintenanceFacade;
    @Autowired
    private SensorRepository sensorRepository;
    @Autowired
    private SensorReadingRepository sensorReadingRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private PlantMetricSampleRepository plantMetricSampleRepository;
    @Autowired
    private PumpRepository pumpRepository;
    @Autowired
    private PumpStateReadingRepository pumpStateReadingRepository;
    @Autowired
    private ZigbeeDeviceSnapshotRepository zigbeeDeviceRepository;
    @Autowired
    private ZigbeeDeviceStateEventRepository zigbeeEventRepository;
    @Autowired
    private ZigbeeDevicePropertyReadingRepository zigbeePropertyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM history_retention_state");
        jdbcTemplate.update("DELETE FROM zigbee_device_property_readings");
        jdbcTemplate.update("DELETE FROM zigbee_device_state_events");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
        jdbcTemplate.update("DELETE FROM pump_state_readings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM sensor_readings");
        jdbcTemplate.update("DELETE FROM sensors");
    }

    @Test
    void keepsHourlyValuesWateringEventsAndStateTransitions() {
        LocalDateTime fromTs = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(40)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime hour = fromTs.plusHours(3);
        seedSensor(hour);
        seedPlant(hour);
        seedPump(hour);
        seedZigbee(hour);

        HistoryRetentionResult result = maintenanceFacade.compactNextDay();

        Assertions.assertEquals(fromTs.toLocalDate(), result.day());
        Assertions.assertEquals(16, result.totalRowsDeleted());
        Assertions.assertEquals(1, sensorReadingRepository.count());
        Assertions.assertEquals(3, plantMetricSampleRepository.count());
        Assertions.assertEquals(3, pumpStateReadingRepository.count());
        Assertions.assertEquals(5, zigbeePropertyRepository.count());
        Assertions.assertEquals(5, zigbeeEventRepository.count());
        Assertions.assertEquals(2, countZigbeeProperty("action"));
        Assertions.assertEquals(0, countZigbeeProperty("countdown"));
    }

    private void seedSensor(LocalDateTime hour) {
        SensorEntity sensor = SensorEntity.create();
        sensor.setDeviceId(1);
        sensor.setType(SensorType.AIR_TEMPERATURE);
        sensor.setChannel(0);
        sensor.setDetected(true);
        sensor = sensorRepository.save(sensor);
        for (int minute : List.of(5, 15, 55)) {
            SensorReadingEntity reading = SensorReadingEntity.create();
            reading.setSensor(sensor);
            reading.setTs(hour.plusMinutes(minute));
            reading.setValueNumeric(20.0 + minute);
            sensorReadingRepository.save(reading);
        }
    }

    private void seedPlant(LocalDateTime hour) {
        PlantEntity plant = PlantEntity.create();
        plant.setName("Retention plant");
        plant.setUserId(1);
        plant.setPlantedAt(hour.minusDays(10));
        plant = plantRepository.save(plant);
        for (int minute : List.of(5, 15, 55)) {
            plantMetricSampleRepository.save(plantSample(
                    plant,
                    PlantMetricType.SOIL_MOISTURE,
                    hour.plusMinutes(minute),
                    30.0 + minute
            ));
        }
        plantMetricSampleRepository.save(plantSample(
                plant,
                PlantMetricType.WATERING_VOLUME_L,
                hour.plusMinutes(20),
                0.2
        ));
        plantMetricSampleRepository.save(plantSample(
                plant,
                PlantMetricType.WATERING_VOLUME_L,
                hour.plusMinutes(40),
                0.3
        ));
    }

    private PlantMetricSampleEntity plantSample(
            PlantEntity plant,
            PlantMetricType type,
            LocalDateTime ts,
            double value
    ) {
        PlantMetricSampleEntity sample = PlantMetricSampleEntity.create();
        sample.setPlant(plant);
        sample.setMetricType(type);
        sample.setTs(ts);
        sample.setValueNumeric(value);
        return sample;
    }

    private void seedPump(LocalDateTime hour) {
        PumpEntity pump = PumpEntity.create();
        pump.setDeviceId(1);
        pump.setChannel(0);
        pump.setLabel("Retention pump");
        pump = pumpRepository.save(pump);
        boolean[] states = {false, false, true, true, false};
        for (int index = 0; index < states.length; index++) {
            PumpStateReadingEntity reading = PumpStateReadingEntity.create();
            reading.setPump(pump);
            reading.setTs(hour.plusMinutes(index * 5L));
            reading.setIsRunning(states[index]);
            reading.setRawStatus(states[index] ? "running" : "stopped");
            reading.setCreatedAt(hour);
            pumpStateReadingRepository.save(reading);
        }
    }

    private void seedZigbee(LocalDateTime hour) {
        ZigbeeDeviceSnapshotEntity device = ZigbeeDeviceSnapshotEntity.create(1, "retention-zigbee", hour);
        device.setIeeeAddress("0xretention");
        device = zigbeeDeviceRepository.save(device);
        zigbeeReading(device, hour.plusMinutes(1), "temperature", 20.0, null, null);
        zigbeeReading(device, hour.plusMinutes(2), "temperature", 21.0, null, null);
        zigbeeReading(device, hour.plusMinutes(3), "temperature", 22.0, null, null);
        zigbeeReading(device, hour.plusMinutes(4), "state", null, "OFF", null);
        zigbeeReading(device, hour.plusMinutes(5), "state", null, "OFF", null);
        zigbeeReading(device, hour.plusMinutes(6), "state", null, "ON", null);
        zigbeeReading(device, hour.plusMinutes(7), "state", null, "ON", null);
        zigbeeReading(device, hour.plusMinutes(8), "action", null, "single", null);
        zigbeeReading(device, hour.plusMinutes(9), "action", null, "single", null);
        zigbeeReading(device, hour.plusMinutes(10), "countdown", 0.0, null, null);
    }

    private void zigbeeReading(
            ZigbeeDeviceSnapshotEntity device,
            LocalDateTime ts,
            String property,
            Double numeric,
            String text,
            Boolean bool
    ) {
        ZigbeeDeviceStateEventEntity event = ZigbeeDeviceStateEventEntity.create(1);
        event.setDeviceSnapshot(device);
        event.setIeeeAddress(device.getIeeeAddress());
        event.setFriendlyName(device.getFriendlyName());
        event.setTs(ts);
        event.setRawStateJson("{}");
        event.setCreatedAt(ts);
        event = zigbeeEventRepository.save(event);

        ZigbeeDevicePropertyReadingEntity reading = ZigbeeDevicePropertyReadingEntity.create(1);
        reading.setStateEvent(event);
        reading.setDeviceSnapshot(device);
        reading.setIeeeAddress(device.getIeeeAddress());
        reading.setFriendlyName(device.getFriendlyName());
        reading.setProperty(property);
        reading.setTs(ts);
        reading.setValueNumeric(numeric);
        reading.setValueText(text != null ? text : numeric != null ? numeric.toString() : null);
        reading.setValueBoolean(bool);
        reading.setCreatedAt(ts);
        zigbeePropertyRepository.save(reading);
    }

    private int countZigbeeProperty(String property) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zigbee_device_property_readings WHERE property = ?",
                Integer.class,
                property
        );
    }
}
