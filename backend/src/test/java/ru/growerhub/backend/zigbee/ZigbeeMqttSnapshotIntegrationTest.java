package ru.growerhub.backend.zigbee;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.mqtt.MqttMessageHandler;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MQTT_HOST="
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZigbeeMqttSnapshotIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MqttMessageHandler mqttMessageHandler;

    @Autowired
    private ZigbeeFacade zigbeeFacade;

    @Autowired
    private ZigbeeBridgeSnapshotRepository bridgeRepository;

    @Autowired
    private ZigbeeDeviceSnapshotRepository deviceRepository;

    @Autowired
    private ZigbeeCommandResponseSnapshotRepository commandResponseRepository;

    @BeforeEach
    void setUp() {
        commandResponseRepository.deleteAll();
        deviceRepository.deleteAll();
        bridgeRepository.deleteAll();
    }

    @Test
    void storesBridgeDevicesAndSmartPlugState() {
        inject("zigbee2growerhub/bridge/state", "{\"state\":\"online\"}");
        inject("zigbee2growerhub/bridge/info", """
                {"version":"2.12.0","permit_join":false,"permit_join_end":null,"coordinator":{"ieee_address":"0x00124b002c7a2966","type":"zStack3x0"}}
                """);
        inject("zigbee2growerhub/bridge/devices", """
                [
                  {"friendly_name":"Coordinator","ieee_address":"0x00124b002c7a2966","type":"Coordinator","supported":true,"disabled":false},
                  {"friendly_name":"smartplug1","ieee_address":"0xa4c13895af2c1df3","type":"Router","supported":true,"disabled":false,
                   "definition":{"model":"TS011F_plug_1_1","exposes":[{"type":"switch","features":[{"property":"state","access":7}]}]}}
                ]
                """);
        inject("zigbee2growerhub/smartplug1/availability", "{\"state\":\"online\"}");
        inject("zigbee2growerhub/smartplug1", "{\"state\":\"ON\",\"power\":12.5,\"current\":0.1,\"voltage\":220,\"energy\":1.5,\"linkquality\":150}");

        ZigbeeOverviewData overview = zigbeeFacade.getOverview();

        Assertions.assertEquals("online", overview.bridge().state());
        Assertions.assertEquals("2.12.0", overview.bridge().version());
        Assertions.assertNotNull(overview.coordinator());
        Assertions.assertEquals("0x00124b002c7a2966", overview.coordinator().ieeeAddress());

        ZigbeeDeviceData plug = overview.devices().stream()
                .filter(device -> "smartplug1".equals(device.friendlyName()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(plug);
        Assertions.assertEquals("0xa4c13895af2c1df3", plug.ieeeAddress());
        Assertions.assertEquals("online", plug.availability());
        Assertions.assertTrue(plug.state() instanceof Map<?, ?>);
        Assertions.assertEquals("ON", ((Map<?, ?>) plug.state()).get("state"));
        Assertions.assertEquals(12.5, ((Number) ((Map<?, ?>) plug.state()).get("power")).doubleValue());
    }

    @Test
    void storesCommandResponseSnapshot() {
        inject("zigbee2growerhub/bridge/response/device/rename", """
                {"status":"ok","data":{"from":"smartplug1","to":"plug-main","homeassistant_rename":false}}
                """);

        ZigbeeOverviewData overview = zigbeeFacade.getOverview();

        Assertions.assertNotNull(overview.lastCommandResponse());
        Assertions.assertEquals("zigbee2growerhub/bridge/response/device/rename", overview.lastCommandResponse().topic());
        Assertions.assertEquals("ok", overview.lastCommandResponse().status());
    }

    private void inject(String topic, String payload) {
        mqttMessageHandler.handleInboundMessage(topic, payload.getBytes(StandardCharsets.UTF_8));
    }
}
