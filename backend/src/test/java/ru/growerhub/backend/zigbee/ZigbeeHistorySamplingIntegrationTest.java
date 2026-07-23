package ru.growerhub.backend.zigbee;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;

@SpringBootTest
class ZigbeeHistorySamplingIntegrationTest extends IntegrationTestBase {
    @Autowired
    private ZigbeeFacade zigbeeFacade;

    @Autowired
    private ZigbeeDeviceSnapshotRepository deviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM zigbee_device_property_readings");
        jdbcTemplate.update("DELETE FROM zigbee_device_state_events");
        jdbcTemplate.update("DELETE FROM zigbee_device_snapshots");
    }

    @Test
    void writesChangesEventsAndSampledNumbersOnly() {
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);

        state(start, "OFF", 20.0, 100, 0, null);
        state(start.plusMinutes(1), "OFF", 20.1, 100, 0, null);
        state(start.plusMinutes(2), "OFF", 21.0, 100, 0, null);
        state(start.plusMinutes(3), "OFF", 21.0, 100, 0, "single");
        state(start.plusMinutes(4), "OFF", 21.0, 100, 0, "single");
        state(start.plusMinutes(8), "ON", 21.0, 100, 0, null);

        Assertions.assertEquals(1, propertyCount("state", "OFF"));
        Assertions.assertEquals(1, propertyCount("state", "ON"));
        Assertions.assertEquals(3, propertyCount("temperature", null));
        Assertions.assertEquals(2, propertyCount("battery", null));
        Assertions.assertEquals(2, propertyCount("action", "single"));
        Assertions.assertEquals(0, propertyCount("countdown", null));
        Assertions.assertEquals(5, stateEventCount());

        ZigbeeDeviceSnapshotEntity snapshot = deviceRepository
                .findByCoordinatorIdAndFriendlyName(1, "sampling-sensor")
                .orElseThrow();
        Assertions.assertTrue(snapshot.getStateJson().contains("\"state\":\"ON\""));
        Assertions.assertTrue(snapshot.getHistoryCheckpointJson().contains("temperature"));
    }

    private void state(
            LocalDateTime receivedAt,
            String state,
            double temperature,
            int battery,
            int countdown,
            String action
    ) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("state", state);
        payload.put("temperature", temperature);
        payload.put("battery", battery);
        payload.put("countdown", countdown);
        if (action != null) {
            payload.put("action", action);
        }
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                ZigbeeMqttMessageType.DEVICE_STATE,
                "zigbee2growerhub/sampling-sensor",
                "sampling-sensor",
                "sampling-sensor",
                toJson(payload),
                payload,
                receivedAt
        ));
    }

    private int propertyCount(String property, String valueText) {
        if (valueText == null) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM zigbee_device_property_readings WHERE property = ?",
                    Integer.class,
                    property
            );
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zigbee_device_property_readings WHERE property = ? AND value_text = ?",
                Integer.class,
                property,
                valueText
        );
    }

    private int stateEventCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM zigbee_device_state_events",
                Integer.class
        );
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder result = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (result.length() > 1) {
                result.append(',');
            }
            result.append('"').append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String text) {
                result.append('"').append(text).append('"');
            } else {
                result.append(entry.getValue());
            }
        }
        return result.append('}').toString();
    }
}
