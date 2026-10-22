package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;
import ru.growerhub.backend.common.config.zigbee.ZigbeeSelfServiceSettings;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.PushokConnection;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushokCloudBridgeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PushokCloudClient cloud = mock(PushokCloudClient.class);
    private final MqttClient mqtt = mock(MqttClient.class);
    private final Map<String, JsonNode> published = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>(Map.of(
            "00124B0000000001", "socket-before", "00124B0000000003", "socket-after"));

    private Object session() throws Exception {
        var settings = new PushokSettings();
        settings.setRequestTimeoutSeconds(8);
        var bridge = new PushokCloudBridge(mock(ZigbeeFacade.class), settings,
                new ZigbeeSelfServiceSettings(), mock(PushokCredentials.class), mapper);
        var connection = new PushokConnection(1, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "pushok-ABCDEF-1234", "test", "gh/z2m/test", "mqtts://example.invalid:8883",
                "encrypted", "pinned-key", "ACTIVE", LocalDateTime.of(2026, 10, 8, 0, 0));
        var constructor = Class.forName(PushokCloudBridge.class.getName() + "$Session")
                .getDeclaredConstructor(PushokCloudBridge.class, PushokConnection.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(bridge, connection);
        ReflectionTestUtils.setField(session, "cloud", cloud);
        ReflectionTestUtils.setField(session, "mqtt", mqtt);
        doAnswer(invocation -> {
            published.put(invocation.getArgument(0), mapper.readTree(((MqttMessage) invocation.getArgument(1)).getPayload()));
            return null;
        }).when(mqtt).publish(anyString(), any(MqttMessage.class));
        when(cloud.request(eq("getAttributes"), any())).thenAnswer(invocation -> {
            String id = ((Map<?, ?>) invocation.getArgument(1)).get("id").toString();
            if (!names.containsKey(id)) throw new PushokCloudClient.Failure("HUB_COMMAND_REJECTED");
            return mapper.createObjectNode().put("name", names.get(id));
        });
        when(cloud.request(eq("getAdapter"), any())).thenReturn(mapper.readTree("""
            {"params":[{"address":0,"access":"rw","type":"bool","viewParams":{"name":"state"}}]}
            """));
        when(cloud.request(eq("getState"), any())).thenReturn(mapper.readTree("""
            {"0":{"ack":true,"value":true,"time":"100"},"251":{"ack":true,"value":false,"time":"100"}}
            """));
        return session;
    }

    private JsonNode list(String driver) throws Exception {
        var unknown = mapper.createObjectNode().put("id", "00124B0000000002").put("mdl", "Unrecognized");
        if (!"missing".equals(driver)) unknown.set("drv", mapper.readTree(driver));
        return mapper.createArrayNode()
                .add(mapper.createObjectNode().put("id", "00124B0000000001").put("drv", "socket"))
                .add(unknown)
                .add(mapper.createObjectNode().put("id", "00124B0000000003").put("drv", "socket"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "\"\"", "\" \"", "true"})
    void deviceWithoutDriverDoesNotBlockKnownDevicesOrExposeControls(String driver) throws Exception {
        Object session = session();
        when(cloud.request(eq("listObjects"), any())).thenReturn(list(driver));

        ReflectionTestUtils.invokeMethod(session, "refresh");

        JsonNode devices = published.get("gh/z2m/test/bridge/devices");
        assertThat(devices.size()).isEqualTo(3);
        JsonNode unknown = devices.get(1);
        assertThat(unknown.path("ieee_address").asText()).isEqualTo("0x00124b0000000002");
        assertThat(unknown.path("supported").asBoolean()).isFalse();
        assertThat(unknown.path("definition").path("exposes").size()).isZero();
        assertThat(published.get("gh/z2m/test/0x00124b0000000002").size()).isZero();
        for (String name : new String[] {"socket-before", "socket-after"}) {
            assertThat(published.get("gh/z2m/test/" + name).path("state").asText()).isEqualTo("ON");
            assertThat(published.get("gh/z2m/test/" + name + "/availability").path("state").asText()).isEqualTo("online");
        }
        var params = Map.of("id", "00124B0000000002", "type", "zigbee");
        verify(cloud, never()).request("getAttributes", params);
        verify(cloud, never()).request("getState", params);
        verify(cloud, never()).request(eq("setState"), any());
    }

    @Test
    void newlyAvailableDriverIsLoadedOnTheNextRefresh() throws Exception {
        Object session = session();
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("missing"));
        ReflectionTestUtils.invokeMethod(session, "refresh");
        assertThat(published.get("gh/z2m/test/bridge/devices").get(1).path("supported").asBoolean()).isFalse();

        names.put("00124B0000000002", "recognized-relay");
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("\"socket\""));
        ReflectionTestUtils.invokeMethod(session, "refresh");

        JsonNode recognized = published.get("gh/z2m/test/bridge/devices").get(1);
        assertThat(recognized.path("supported").asBoolean()).isTrue();
        assertThat(recognized.path("friendly_name").asText()).isEqualTo("recognized-relay");
        assertThat(recognized.path("definition").path("exposes").get(0).path("property").asText()).isEqualTo("state");
        assertThat(published.get("gh/z2m/test/recognized-relay").path("state").asText()).isEqualTo("ON");
        verify(cloud, never()).request(eq("setState"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"getAttributes", "getAdapter", "getState"})
    void rejectedDeviceReadKeepsOtherDevicesWorkingAndDoesNotRepublishOldState(String method) throws Exception {
        Object session = session();
        names.put("00124B0000000002", "middle-socket");
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("\"socket\""));
        ReflectionTestUtils.invokeMethod(session, "refresh");
        JsonNode adapter = mapper.readTree("{\"params\":[{\"address\":0,\"access\":\"rw\",\"type\":\"bool\",\"viewParams\":{\"name\":\"state\"}}]}");
        JsonNode updated = list("\"changed-driver\"");
        when(cloud.request(eq("listObjects"), any())).thenReturn(updated);
        var params = "getAdapter".equals(method) ? Map.of("drv", "changed-driver")
                : Map.of("id", "00124B0000000002", "type", "zigbee");
        when(cloud.request(eq(method), eq(params))).thenThrow(new PushokCloudClient.Failure("HUB_COMMAND_REJECTED", method));
        published.clear();

        ReflectionTestUtils.invokeMethod(session, "refresh");

        assertThat(published.get("gh/z2m/test/bridge/devices").size()).isEqualTo(3);
        assertThat(published.get("gh/z2m/test/bridge/devices").get(1).path("supported").asBoolean()).isTrue();
        assertThat(published).doesNotContainKey("gh/z2m/test/middle-socket");
        assertThat(published.get("gh/z2m/test/middle-socket/availability").path("state").asText()).isEqualTo("offline");
        for (String name : new String[] {"socket-before", "socket-after"})
            assertThat(published.get("gh/z2m/test/" + name + "/availability").path("state").asText()).isEqualTo("online");

        when(cloud.request(eq(method), eq(params))).thenReturn(switch (method) {
            case "getAttributes" -> mapper.createObjectNode().put("name", "middle-socket");
            case "getAdapter" -> adapter;
            default -> mapper.readTree("{\"0\":{\"ack\":true,\"value\":false,\"time\":\"200\"},\"251\":{\"ack\":true,\"value\":false,\"time\":\"200\"}}");
        });
        published.clear();
        ReflectionTestUtils.invokeMethod(session, "refresh");
        assertThat(published.get("gh/z2m/test/middle-socket/availability").path("state").asText()).isEqualTo("online");
        verify(cloud, never()).request(eq("setState"), any());
    }

    @Test
    void transportFailureStillStopsTheRefreshForReconnect() throws Exception {
        Object session = session();
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("missing"));
        when(cloud.request(eq("getState"), any())).thenThrow(new PushokCloudClient.Failure("CLOUD_UNAVAILABLE"));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(session, "refresh"))
                .isInstanceOf(PushokCloudClient.Failure.class).hasMessage("CLOUD_UNAVAILABLE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ambiguousNamesAndExpiredCommandsAreRejectedWithoutSendingOrRetrying() throws Exception {
        Object session = session();
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("missing"));
        ReflectionTestUtils.invokeMethod(session, "refresh");
        var devices = (Map<String, PushokDevice>) ReflectionTestUtils.getField(session, "devices");
        var duplicate = new PushokDevice(mapper.readTree("{\"id\":\"00124B0000000004\"}"), "socket-before",
                mapper.readTree("{\"params\":[{\"address\":0,\"access\":\"rw\",\"type\":\"bool\",\"viewParams\":{\"name\":\"state\"}}]}"));
        duplicate.update(mapper.readTree("{\"0\":{\"ack\":true,\"value\":true},\"251\":{\"ack\":true,\"value\":false}}"));
        devices.put(duplicate.id(), duplicate);
        var queue = (ArrayBlockingQueue<Object>) ReflectionTestUtils.getField(session, "commands");
        var constructor = Class.forName(PushokCloudBridge.class.getName() + "$Command")
                .getDeclaredConstructor(String.class, JsonNode.class, long.class);
        constructor.setAccessible(true);
        queue.add(constructor.newInstance("socket-before/set", mapper.readTree("{\"state\":\"OFF\"}"), System.currentTimeMillis()));
        queue.add(constructor.newInstance("socket-after/set", mapper.readTree("{\"state\":\"OFF\"}"), 0L));

        ReflectionTestUtils.invokeMethod(session, "drainCommands");
        ReflectionTestUtils.invokeMethod(session, "drainCommands");

        verify(cloud, never()).request(eq("setState"), any());
        assertThat(queue).isEmpty();
        assertThat(published.get("gh/z2m/test/bridge/response/device/set").path("status").asText()).isEqualTo("error");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingCommandIsSentBetweenDeviceReadsAndNeverPretendsToBeConfirmedState() throws Exception {
        Object session = session();
        when(cloud.request(eq("listObjects"), any())).thenReturn(list("missing"));
        ReflectionTestUtils.invokeMethod(session, "refresh");
        clearInvocations(cloud);
        when(cloud.request(eq("setState"), any())).thenReturn(mapper.getNodeFactory().booleanNode(true));
        var queue = (ArrayBlockingQueue<Object>) ReflectionTestUtils.getField(session, "commands");
        var constructor = Class.forName(PushokCloudBridge.class.getName() + "$Command")
                .getDeclaredConstructor(String.class, JsonNode.class, long.class);
        constructor.setAccessible(true);
        when(cloud.request(eq("getState"), any())).thenAnswer(invocation -> {
            if ("00124B0000000001".equals(((Map<?, ?>) invocation.getArgument(1)).get("id")))
                queue.add(constructor.newInstance("socket-after/set", mapper.readTree("{\"state\":\"OFF\"}"), System.currentTimeMillis()));
            return mapper.readTree("{\"0\":{\"ack\":true,\"value\":true,\"time\":\"200\"},\"251\":{\"ack\":true,\"value\":false,\"time\":\"200\"}}");
        });
        published.clear();

        ReflectionTestUtils.invokeMethod(session, "refresh");
        ReflectionTestUtils.invokeMethod(session, "drainCommands");

        var order = inOrder(cloud);
        order.verify(cloud).request("getState", Map.of("id", "00124B0000000001", "type", "zigbee"));
        order.verify(cloud).request("setState", Map.of("id", "00124B0000000003", "type", "zigbee", "field", 0, "value", false));
        order.verify(cloud).request("getState", Map.of("id", "00124B0000000003", "type", "zigbee"));
        verify(cloud, times(1)).request(eq("setState"), any());
        assertThat(published.get("gh/z2m/test/bridge/response/device/set").path("status").asText()).isEqualTo("ok");
        assertThat(published.get("gh/z2m/test/socket-after").path("state").asText()).isEqualTo("ON");
    }
}
