package ru.growerhub.backend.zigbee;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.zigbee.engine.ZigbeeWaterMeter;

class ZigbeeWaterMeterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertsCubicMetersPerHourWithoutChangingReportedLiters() throws Exception {
        var definition = mapper.readTree("""
                {"exposes":[{"property":"flow","unit":"m³/h"}]}
                """);
        var observation = ZigbeeWaterMeter.normalize(definition, mapper.readTree("""
                {"state":"OFF","flow":0,"real_time_irrigation_volume":42,
                 "irrigation_start_time":1790800433,"irrigation_end_time":1790802224}
                """), LocalDateTime.parse("2026-10-01T20:00:00"));
        assertThat(observation.complete()).isTrue();
        assertThat(observation.volumeL()).isEqualTo(42);
        var flowing = ZigbeeWaterMeter.normalize(definition, mapper.readTree("{\"state\":\"ON\",\"flow\":0.6}"),
                LocalDateTime.parse("2026-10-01T20:00:00"));
        assertThat(flowing.flowLMin()).isCloseTo(10, within(0.00001));
        assertThat(flowing.complete()).isFalse();
    }

    @Test
    void oldEndTimestampWhileOpenAndFlowAfterOffDoNotProveFinishedWatering() throws Exception {
        var definition = mapper.readTree("{\"exposes\":[{\"property\":\"flow\",\"unit\":\"L/min\"}]}");
        for (String state : new String[]{"ON", "OFF"}) {
            var observation = ZigbeeWaterMeter.normalize(definition, mapper.readTree("""
                    {"state":"%s","flow":2,"real_time_irrigation_volume":250,
                     "irrigation_start_time":1790800433,"irrigation_end_time":1790802224}
                    """.formatted(state)), LocalDateTime.parse("2026-10-01T20:00:00"));
            assertThat(observation.complete()).isFalse();
        }
        var future = ZigbeeWaterMeter.normalize(definition, mapper.readTree("""
                {"state":"OFF","real_time_irrigation_volume":250,
                 "irrigation_start_time":1790800433,"irrigation_end_time":99999999999}
                """), LocalDateTime.parse("2026-10-01T20:00:00"));
        assertThat(future.complete()).isFalse();
    }
}
