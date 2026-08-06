package ru.growerhub.backend.zigbee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.zigbee.contract.ZigbeePowerStatistics;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventRepository;

@SpringBootTest(properties = "history.retention.startupDelayMs=999999999")
@Import(ZigbeePowerStatisticsIntegrationTest.FixedClockConfig.class)
class ZigbeePowerStatisticsIntegrationTest extends IntegrationTestBase {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    @Autowired
    private ZigbeeFacade zigbeeFacade;
    @Autowired
    private ZigbeeDeviceSnapshotRepository deviceRepository;
    @Autowired
    private ZigbeeDeviceStateEventRepository eventRepository;
    @Autowired
    private ZigbeeDevicePropertyReadingRepository readingRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM zigbee_device_property_readings");
        jdbcTemplate.update("DELETE FROM zigbee_device_state_events");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
    }

    @Test
    void returnsPowerDailyDurationAndCumulativeEnergy() {
        Instant now = NOW;
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        ZigbeeDeviceSnapshotEntity device = device(
                "0xpower",
                "{\"state\":\"OFF\",\"power\":0.25,\"energy\":700}",
                """
                        {"definition":{"exposes":[
                          {"type":"binary","property":"state","access":3},
                          {"type":"numeric","property":"power","access":1,"unit":"kW"},
                          {"type":"numeric","property":"energy","access":1,"unit":"Wh"}
                        ]}}
                        """
        );

        reading(device, yesterdayStart.minusSeconds(60), "state", null, "OFF");
        reading(device, yesterdayStart.plusSeconds(22 * 3600), "state", null, "ON");
        reading(device, todayStart, "state", null, "OFF");
        reading(device, yesterdayStart, "energy", 10000.0, null);
        reading(device, yesterdayStart.plusSeconds(12 * 3600), "energy", 11000.0, null);
        reading(device, yesterdayStart.plusSeconds(18 * 3600), "energy", 200.0, null);
        reading(device, yesterdayStart.plusSeconds(23 * 3600), "energy", 700.0, null);
        reading(device, now.minusSeconds(3600), "energy", 900.0, null);
        reading(device, now.minusSeconds(1200), "power", 0.0, null);
        reading(device, now.minusSeconds(600), "power", 0.1, null);
        reading(device, now.minusSeconds(300), "power", 0.25, null);

        ZigbeePowerStatistics result = zigbeeFacade.getPowerStatisticsForAutomation(
                1,
                device.getIeeeAddress(),
                "state",
                "ON",
                24,
                "UTC"
        );

        Assertions.assertEquals("power", result.chartKind());
        Assertions.assertEquals("W", result.chartUnit());
        Assertions.assertEquals(List.of(0.0, 100.0, 250.0), result.points().stream()
                .map(point -> point.value())
                .toList());
        Assertions.assertTrue(result.energySupported());
        Assertions.assertEquals(7, result.daily().size());
        ZigbeePowerStatistics.DailyUsage yesterday = result.daily().stream()
                .filter(day -> day.date().equals(today.minusDays(1)))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(2 * 3600L, yesterday.onDurationSeconds());
        Assertions.assertEquals(1.5, yesterday.energyKwh(), 0.000001);
        ZigbeePowerStatistics.DailyUsage current = result.daily().stream()
                .filter(day -> day.date().equals(today))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(0.2, current.energyKwh(), 0.000001);
        Assertions.assertTrue(current.partial());
    }

    @Test
    void fallsBackToBinaryWhenPowerAndEnergyAreUnavailable() {
        Instant now = NOW;
        ZigbeeDeviceSnapshotEntity device = device(
                "0xbinary",
                "{\"state\":\"ON\"}",
                "{\"definition\":{\"exposes\":[{\"type\":\"binary\",\"property\":\"state\",\"access\":3}]}}"
        );
        reading(device, now.minusSeconds(1800), "state", null, "OFF");
        reading(device, now.minusSeconds(900), "state", null, "ON");

        ZigbeePowerStatistics result = zigbeeFacade.getPowerStatisticsForAutomation(
                1,
                device.getIeeeAddress(),
                "state",
                "ON",
                24,
                "Europe/Moscow"
        );

        Assertions.assertEquals("binary", result.chartKind());
        Assertions.assertNull(result.chartUnit());
        Assertions.assertEquals(List.of(0.0, 1.0), result.points().stream()
                .map(point -> point.value())
                .toList());
        Assertions.assertFalse(result.energySupported());
        Assertions.assertTrue(result.daily().stream().allMatch(day -> day.energyKwh() == null));
    }

    @Test
    void splitsOnDurationAtLocalMidnightAcrossDstOffsetChange() {
        ZigbeeDeviceSnapshotEntity device = device(
                "0xdst",
                "{\"state\":\"OFF\"}",
                "{\"definition\":{\"exposes\":[{\"type\":\"binary\",\"property\":\"state\",\"access\":3}]}}"
        );
        reading(device, Instant.parse("2026-10-19T21:59:00Z"), "state", null, "OFF");
        reading(device, Instant.parse("2026-10-24T21:00:00Z"), "state", null, "ON");
        reading(device, Instant.parse("2026-10-25T02:00:00Z"), "state", null, "OFF");

        ZigbeePowerStatistics result = zigbeeFacade.getPowerStatisticsForAutomation(
                1,
                device.getIeeeAddress(),
                "state",
                "ON",
                168,
                "Europe/Berlin"
        );

        Assertions.assertEquals(3600L, daily(result, LocalDate.of(2026, 10, 24)).onDurationSeconds());
        Assertions.assertEquals(4 * 3600L, daily(result, LocalDate.of(2026, 10, 25)).onDurationSeconds());
    }

    @Test
    void returnsNullEnergyWhenDayHasNoBaselineReading() {
        ZigbeeDeviceSnapshotEntity device = device(
                "0xshort-energy",
                "{\"state\":\"OFF\",\"energy\":12.5}",
                """
                        {"definition":{"exposes":[
                          {"type":"binary","property":"state","access":3},
                          {"type":"numeric","property":"energy","access":1,"unit":"kWh"}
                        ]}}
                        """
        );
        LocalDate today = NOW.atZone(ZoneOffset.UTC).toLocalDate();
        reading(device, today.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(60), "state", null, "OFF");
        reading(device, NOW.minusSeconds(600), "energy", 12.5, null);

        ZigbeePowerStatistics result = zigbeeFacade.getPowerStatisticsForAutomation(
                1,
                device.getIeeeAddress(),
                "state",
                "ON",
                24,
                "UTC"
        );

        Assertions.assertTrue(result.energySupported());
        Assertions.assertNull(daily(result, today).energyKwh());
    }

    private ZigbeePowerStatistics.DailyUsage daily(ZigbeePowerStatistics statistics, LocalDate date) {
        return statistics.daily().stream()
                .filter(day -> day.date().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private ZigbeeDeviceSnapshotEntity device(String ieeeAddress, String stateJson, String bridgeDeviceJson) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ZigbeeDeviceSnapshotEntity device = ZigbeeDeviceSnapshotEntity.create(1, ieeeAddress, now);
        device.setIeeeAddress(ieeeAddress);
        device.setStateJson(stateJson);
        device.setBridgeDeviceJson(bridgeDeviceJson);
        device.setLastStateAt(now);
        return deviceRepository.save(device);
    }

    private void reading(
            ZigbeeDeviceSnapshotEntity device,
            Instant instant,
            String property,
            Double numeric,
            String text
    ) {
        LocalDateTime timestamp = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        ZigbeeDeviceStateEventEntity event = ZigbeeDeviceStateEventEntity.create(1);
        event.setDeviceSnapshot(device);
        event.setIeeeAddress(device.getIeeeAddress());
        event.setFriendlyName(device.getFriendlyName());
        event.setTs(timestamp);
        event.setRawStateJson("{}");
        event.setCreatedAt(timestamp);
        event = eventRepository.save(event);

        ZigbeeDevicePropertyReadingEntity reading = ZigbeeDevicePropertyReadingEntity.create(1);
        reading.setStateEvent(event);
        reading.setDeviceSnapshot(device);
        reading.setIeeeAddress(device.getIeeeAddress());
        reading.setFriendlyName(device.getFriendlyName());
        reading.setProperty(property);
        reading.setTs(timestamp);
        reading.setValueNumeric(numeric);
        reading.setValueText(text != null ? text : numeric != null ? numeric.toString() : null);
        reading.setCreatedAt(timestamp);
        readingRepository.save(reading);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock statisticsClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
