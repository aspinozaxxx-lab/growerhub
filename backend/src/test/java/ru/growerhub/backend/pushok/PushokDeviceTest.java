package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PushokDeviceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private PushokDevice device() throws Exception {
        return new PushokDevice(mapper.readTree("{\"id\":\"A4C138C9DBABE55B\",\"mdl\":\"TS011F\",\"mnf\":\"Zbeacon\"}"), "Socket", mapper.readTree("""
            {"params":[
              {"address":0,"access":"rw","type":"bool","viewParams":{"name":"state","type":"switch"}},
              {"address":1,"access":"r","type":"int","viewParams":{"name":"voltage","unit":"unit_voltage"}},
              {"address":2,"access":"r","type":"int","viewParams":{"name":"current","unit":"unit_mA"}},
              {"address":4,"access":"rw","type":"int","min":0,"max":254,"viewParams":{"name":"brightness"}},
              {"address":5,"access":"rw","type":"int","labels":{"off":0,"previous":2},"viewParams":{"name":"power_memory","type":"dropdown"}},
              {"address":250,"access":"rw","type":"int","hidden":true,"viewParams":{"name":"offline_timeout"}}
            ]}
            """));
    }
    @Test void exposesAndValuesUseTheSameUnitsAndPreserveFalse() throws Exception {
        var device = device();
        device.update(mapper.readTree("""
            {"0":{"ack":true,"value":false},"1":{"ack":true,"value":218},"2":{"ack":true,"value":150},"5":{"ack":true,"value":2},"251":{"ack":true,"value":false}}
            """));
        assertThat(device.state()).containsEntry("state", "OFF").containsEntry("current", 0.15).containsEntry("voltage", 218.0).containsEntry("power_memory", "previous");
        String metadata = mapper.writeValueAsString(device.metadata(java.util.List.of("state", "brightness")));
        assertThat(metadata).contains("exposes", "\"unit\":\"A\"", "\"access\":3").doesNotContain("offline_timeout", "energy");
        assertThat(device.command("state", mapper.readTree("\"ON\""))).containsEntry("value", true).containsEntry("field", 0);
    }
    @Test void olderBroadcastCannotOverwriteNewerConfirmedStateOrAvailability() throws Exception {
        var device = device();
        assertThat(device.offline()).isTrue();
        device.update(mapper.readTree("{\"0\":{\"ack\":true,\"value\":true,\"time\":\"200\"},\"251\":{\"ack\":true,\"value\":true,\"time\":\"200\"}}"));
        device.update(mapper.readTree("{\"0\":{\"ack\":true,\"value\":false,\"time\":\"100\"},\"251\":{\"ack\":true,\"value\":false,\"time\":\"100\"}}"));
        assertThat(device.state()).containsEntry("state", "ON");
        assertThat(device.offline()).isTrue();
        var metadata = mapper.valueToTree(device.metadata(java.util.List.of("state")));
        for (var feature : metadata.path("definition").path("exposes"))
            assertThat(feature.path("access").asInt()).isEqualTo("state".equals(feature.path("property").asText()) ? 3 : 1);
    }
    @Test void unconfirmedOrHistoricalUpdatesCannotPretendToBePhysicalState() throws Exception {
        var device = device();
        device.update(mapper.readTree("{\"0\":{\"ack\":true,\"value\":false}}"));
        assertThat(device.update(mapper.readTree("{\"0\":{\"ack\":false,\"value\":true}}"))).isFalse();
        assertThat(device.update(mapper.readTree("{\"0\":{\"ack\":true,\"history\":true,\"value\":true}}"))).isFalse();
        assertThat(device.state()).containsEntry("state", "OFF");
        device.update(mapper.readTree("{\"251\":{\"ack\":true,\"value\":true}}"));
        assertThat(device.offline()).isTrue();
        assertThatThrownBy(() -> device.command("state", mapper.readTree("true"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsReadOnlyUnknownAndOutOfRangeCommands() throws Exception {
        var device = device();
        device.update(mapper.readTree("{\"251\":{\"ack\":true,\"value\":false}}"));
        for (String property : new String[] {"voltage", "offline_timeout", "unknown"})
            assertThatThrownBy(() -> device.command(property, mapper.readTree("1"))).isInstanceOf(IllegalArgumentException.class);
        for (String value : new String[] {"255", "-1", "1.5", "\"12\""})
            assertThatThrownBy(() -> device.command("brightness", mapper.readTree(value))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> device.command("state", mapper.readTree("\"TOGGLE\""))).isInstanceOf(IllegalArgumentException.class);
        assertThat(PushokDevice.name(mapper.readTree("{\"name\":\"A/# +B\"}"), "A4C138C9DBABE55B", mapper)).doesNotContain("/", "#", "+");
    }
    @Test void freshUnchangedMeasurementStillUpdatesHistoryWithoutReplayingOldSnapshot() throws Exception {
        var device = device();
        assertThat(device.update(mapper.readTree("{\"1\":{\"ack\":true,\"value\":220,\"time\":\"100\"}}"))).isTrue();
        assertThat(device.update(mapper.readTree("{\"1\":{\"ack\":true,\"value\":220,\"time\":\"200\"}}"))).isTrue();
        assertThat(device.update(mapper.readTree("{\"1\":{\"ack\":true,\"value\":220,\"time\":\"200\"}}"))).isFalse();
    }
}
