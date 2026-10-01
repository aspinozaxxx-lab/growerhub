package ru.growerhub.backend.zigbee;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.mqtt.MqttMessageHandler;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST=", "SELF_SERVICE_ENABLED=true"}
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

    @Autowired private ZigbeeCoordinatorRepository coordinatorRepository;
    @Autowired private ZigbeeDevicePropertyReadingRepository readingRepository;
    @Autowired private ZigbeeDeviceStateEventRepository eventRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        readingRepository.deleteAll();
        eventRepository.deleteAll();
        commandResponseRepository.deleteAll();
        deviceRepository.deleteAll();
        bridgeRepository.deleteAll();
        coordinatorRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void storesBridgeDevicesAndSmartPlugState(boolean retained) {
        inject("zigbee2growerhub/bridge/state", "{\"state\":\"online\"}", retained);
        inject("zigbee2growerhub/bridge/info", """
                {"version":"2.12.0","permit_join":false,"permit_join_end":null,"coordinator":{"ieee_address":"0x00124b002c7a2966","type":"zStack3x0"}}
                """, retained);
        inject("zigbee2growerhub/bridge/devices", """
                [
                  {"friendly_name":"Coordinator","ieee_address":"0x00124b002c7a2966","type":"Coordinator","supported":true,"disabled":false},
                  {"friendly_name":"smartplug1","ieee_address":"0xa4c13895af2c1df3","type":"Router","supported":true,"disabled":false,
                   "definition":{"model":"TS011F_plug_1_1","exposes":[{"type":"switch","features":[{"property":"state","access":7}]}]}}
                ]
                """, retained);
        inject("zigbee2growerhub/smartplug1/availability", "{\"state\":\"online\"}", retained);
        inject("zigbee2growerhub/smartplug1", "{\"state\":\"ON\",\"power\":12.5,\"current\":0.1,\"voltage\":220,\"energy\":1.5,\"linkquality\":150}", retained);

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
        Assertions.assertEquals(retained ? null : "online", plug.availability());
        Assertions.assertTrue(plug.state() instanceof Map<?, ?>);
        Assertions.assertEquals("ON", ((Map<?, ?>) plug.state()).get("state"));
        Assertions.assertEquals(12.5, ((Number) ((Map<?, ?>) plug.state()).get("power")).doubleValue());
        var stored = deviceRepository.findByCoordinatorIdAndFriendlyName(1, "smartplug1").orElseThrow();
        if (retained) {
            Assertions.assertNull(stored.getLastLiveStateAt());
            Assertions.assertNull(stored.getLiveStateJson());
            Assertions.assertEquals(0, readingRepository.count());
            Assertions.assertEquals(0, eventRepository.count());
        } else {
            Assertions.assertNotNull(stored.getLastLiveStateAt());
            Assertions.assertTrue(stored.getLiveStateJson().contains("ON"));
            inject("zigbee2growerhub/smartplug1", "{\"state\":\"OFF\"}", true);
            var replayed = deviceRepository.findByCoordinatorIdAndFriendlyName(1, "smartplug1").orElseThrow();
            Assertions.assertEquals(stored.getLastLiveStateAt(), replayed.getLastLiveStateAt());
            Assertions.assertTrue(replayed.getLiveStateJson().contains("ON"));
        }
    }

    @Test
    void storesCommandResponseSnapshot() {
        inject("zigbee2growerhub/bridge/response/device/rename", """
                {"status":"ok","data":{"from":"smartplug1","to":"plug-main","homeassistant_rename":false}}
                """, false);

        ZigbeeOverviewData overview = zigbeeFacade.getOverview();

        Assertions.assertNotNull(overview.lastCommandResponse());
        Assertions.assertEquals("zigbee2growerhub/bridge/response/device/rename", overview.lastCommandResponse().topic());
        Assertions.assertEquals("ok", overview.lastCommandResponse().status());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void relayPreservesCacheOriginAndResolvesFullUnicodeName(boolean innerRetained, boolean outerRetained) throws Exception {
        var coordinator = coordinator("relay-a");
        inventory(coordinator, "теплица/датчик");
        relay(coordinator, "теплица/датчик/availability", "{\"state\":\"online\"}", innerRetained, outerRetained);
        relay(coordinator, "теплица/датчик", "{\"temperature\":24}", innerRetained, outerRetained);
        var device = deviceRepository.findByCoordinatorIdAndFriendlyName(coordinator.getId(), "теплица/датчик").orElseThrow();
        Assertions.assertEquals("online", device.getAvailability());
        Assertions.assertEquals("{\"temperature\":24}", device.getStateJson());
        boolean retained = innerRetained || outerRetained;
        Assertions.assertEquals(retained, device.getLastLiveStateAt() == null);
        Assertions.assertEquals(retained ? 0 : 1, readingRepository.count());
        var storedCoordinator = coordinatorRepository.findById(coordinator.getId()).orElseThrow();
        Assertions.assertEquals(retained, storedCoordinator.getLastSeenAt() == null);
        Assertions.assertEquals(retained ? "PROVISIONING" : "ONLINE", storedCoordinator.getStatus().name());
        var publicDevice = zigbeeFacade.getOverview(new AuthenticatedUser(901, "user"), coordinator.getPublicId()).devices().getFirst();
        Assertions.assertEquals(device.getLastLiveStateAt(), publicDevice.lastStateAt());
        Assertions.assertEquals(retained ? null : "online", publicDevice.availability());
    }

    @Test
    void cacheReplayDoesNotChangeLiveTimeHistoryOrCoordinatorLiveness() throws Exception {
        var coordinator = coordinator("cache-a");
        inventory(coordinator, "garden/soil");
        relay(coordinator, "garden/soil", "{\"temperature\":23}", false, false);
        var before = deviceRepository.findByCoordinatorIdAndFriendlyName(coordinator.getId(), "garden/soil").orElseThrow();
        var lastSeen = coordinatorRepository.findById(coordinator.getId()).orElseThrow().getLastSeenAt();
        long readings = readingRepository.count();
        long events = eventRepository.count();
        relay(coordinator, "garden/soil", "{\"temperature\":17}", true, false);
        relay(coordinator, "bridge/state", "{\"state\":\"online\"}", true, false);
        var after = deviceRepository.findById(before.getId()).orElseThrow();
        Assertions.assertEquals(before.getLiveStateJson(), after.getLiveStateJson());
        Assertions.assertEquals(before.getLastLiveStateAt(), after.getLastLiveStateAt());
        Assertions.assertEquals(lastSeen, coordinatorRepository.findById(coordinator.getId()).orElseThrow().getLastSeenAt());
        Assertions.assertEquals(readings, readingRepository.count());
        Assertions.assertEquals(events, eventRepository.count());
        var publicDevice = zigbeeFacade.getOverview(new AuthenticatedUser(901, "user"), coordinator.getPublicId()).devices().getFirst();
        Assertions.assertEquals(before.getLastLiveStateAt(), publicDevice.lastStateAt());
        Assertions.assertEquals(23, ((Map<?, ?>) publicDevice.state()).get("temperature"));
    }

    @Test
    void rawNestedNamesRenameRemovalAndCoordinatorIsolationUseCurrentInventory() throws Exception {
        var first = coordinator("raw-a");
        var second = coordinator("raw-b");
        inventory(first, "garden/soil");
        inventory(second, "garden/soil");
        inject(first.getBaseTopic() + "/garden/soil", "{\"temperature\":21}", false);
        inject(second.getBaseTopic() + "/garden/soil", "{\"temperature\":28}", false);
        Assertions.assertTrue(deviceRepository.findByCoordinatorIdAndIeeeAddress(first.getId(), "0xsame").orElseThrow().getLiveStateJson().contains("21"));
        Assertions.assertTrue(deviceRepository.findByCoordinatorIdAndIeeeAddress(second.getId(), "0xsame").orElseThrow().getLiveStateJson().contains("28"));
        inventory(first, "new/soil");
        inject(first.getBaseTopic() + "/garden/soil", "{\"temperature\":99}", false);
        inject(first.getBaseTopic() + "/new/soil/temperature", "25", false);
        Assertions.assertEquals(2, deviceRepository.count());
        var renamed = deviceRepository.findByCoordinatorIdAndIeeeAddress(first.getId(), "0xsame").orElseThrow();
        Assertions.assertEquals("new/soil", renamed.getFriendlyName());
        Assertions.assertTrue(renamed.getLiveStateJson().contains("21"));
        inject(first.getBaseTopic() + "/new/soil", "{\"temperature\":25}", false);
        inject(first.getBaseTopic() + "/bridge/devices", "[]", true);
        inject(first.getBaseTopic() + "/new/soil", "{\"temperature\":99}", false);
        Assertions.assertTrue(deviceRepository.findById(renamed.getId()).orElseThrow().getLiveStateJson().contains("25"));
        inject("gh/z2m/unissued/bridge/devices", "[{\"friendly_name\":\"intruder\"}]", false);
        Assertions.assertEquals(2, deviceRepository.count());
    }

    @Test
    void ambiguousNamesAndCommandEchoAreNotDeviceStates() throws Exception {
        var coordinator = coordinator("ambiguous-a");
        inject(coordinator.getBaseTopic() + "/bridge/devices", """
                [{"friendly_name":"garden","ieee_address":"0xa"},
                 {"friendly_name":"garden/set","ieee_address":"0xb"},
                 {"friendly_name":"other/availability","ieee_address":"0xc"}]
                """, true);
        for (String topic : List.of("garden", "garden/set", "garden/availability", "garden/get")) {
            inject(coordinator.getBaseTopic() + "/" + topic, "{\"state\":\"ON\"}", false);
        }
        Assertions.assertEquals(0, eventRepository.count());
        Assertions.assertNull(coordinatorRepository.findById(coordinator.getId()).orElseThrow().getLastSeenAt());
        inject(coordinator.getBaseTopic() + "/other/availability", "{\"temperature\":20}", false);
        Assertions.assertNotNull(deviceRepository.findByCoordinatorIdAndFriendlyName(coordinator.getId(), "other/availability").orElseThrow().getLastLiveStateAt());
    }

    @Test
    void malformedOrOversizedRelayIsNeverInterpretedAsRawState() {
        var coordinator = coordinator("invalid-a");
        String topic = coordinator.getBaseTopic() + "/bridge/relay/sensor";
        for (String envelope : List.of("null", "[]", "{\"temperature\":24}",
                "{\"v\":2,\"payload\":\"24\",\"retained\":false}",
                "{\"v\":4294967297,\"payload\":\"24\",\"retained\":false}",
                "{\"v\":1,\"payload\":{},\"retained\":false}",
                "{\"v\":1,\"payload\":\"24\",\"retained\":\"false\"}",
                "{\"v\":1,\"v\":1,\"payload\":\"24\",\"retained\":false}",
                "{\"v\":1,\"payload\":\"24\",\"retained\":false,\"username\":\"other\"}")) {
            inject(topic, envelope, false);
        }
        mqttMessageHandler.handleInboundMessage(topic, new byte[]{(byte) 0xff}, false);
        inject(topic, "x".repeat(1048577), false);
        Assertions.assertEquals(0, deviceRepository.count());
        Assertions.assertEquals(0, readingRepository.count());
        Assertions.assertNull(coordinatorRepository.findById(coordinator.getId()).orElseThrow().getLastSeenAt());
    }

    private ZigbeeCoordinatorEntity coordinator(String username) {
        return coordinatorRepository.save(ZigbeeCoordinatorEntity.create(UUID.randomUUID(), 901, "Test", username,
                "gh/z2m/" + username, LocalDateTime.now()));
    }

    @Test
    void malformedInventoryCannotRemoveKnownRoutesAndCreateUnknownDevices() throws Exception {
        var coordinator = coordinator("inventory-a");
        inventory(coordinator, "soil");
        inject(coordinator.getBaseTopic() + "/bridge/devices", "{}", false);
        inject(coordinator.getBaseTopic() + "/unknown", "{\"temperature\":99}", false);
        Assertions.assertEquals(1, deviceRepository.count());
        Assertions.assertNull(coordinatorRepository.findById(coordinator.getId()).orElseThrow().getLastSeenAt());
        inject(coordinator.getBaseTopic() + "/soil", "{\"temperature\":26}", false);
        Assertions.assertEquals(1, readingRepository.count());
    }

    private void inventory(ZigbeeCoordinatorEntity coordinator, String name) throws Exception {
        inject(coordinator.getBaseTopic() + "/bridge/devices", objectMapper.writeValueAsString(
                List.of(Map.of("friendly_name", name, "ieee_address", "0xsame", "type", "EndDevice"))), true);
    }

    private void relay(ZigbeeCoordinatorEntity coordinator, String relativeTopic, String payload,
            boolean innerRetained, boolean outerRetained) throws Exception {
        inject(coordinator.getBaseTopic() + "/bridge/relay/" + relativeTopic,
                objectMapper.writeValueAsString(Map.of("v", 1, "payload", payload, "retained", innerRetained)), outerRetained);
    }

    private void inject(String topic, String payload, boolean retained) {
        mqttMessageHandler.handleInboundMessage(topic, payload.getBytes(StandardCharsets.UTF_8), retained);
    }
}
