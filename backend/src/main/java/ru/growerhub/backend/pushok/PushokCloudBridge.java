package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;
import ru.growerhub.backend.common.config.zigbee.ZigbeeSelfServiceSettings;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.PushokConnection;

@Component
public class PushokCloudBridge {
    private static final Logger log = LoggerFactory.getLogger(PushokCloudBridge.class);
    private final ZigbeeFacade facade;
    private final PushokSettings settings;
    private final ZigbeeSelfServiceSettings selfService;
    private final PushokCredentials credentials;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> retries = new ConcurrentHashMap<>();
    private volatile boolean stopping;

    public PushokCloudBridge(ZigbeeFacade facade, PushokSettings settings, ZigbeeSelfServiceSettings selfService,
            PushokCredentials credentials, ObjectMapper mapper) {
        this.facade = facade; this.settings = settings; this.selfService = selfService; this.credentials = credentials; this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${zigbee.pushok.reconcile-seconds:3}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void reconcile() {
        if (stopping || !settings.isEnabled()) return;
        var connections = facade.getPushokConnections();
        var wanted = new HashMap<Integer, PushokConnection>();
        connections.forEach(connection -> wanted.put(connection.coordinatorId(), connection));
        sessions.forEach((id, session) -> {
            var current = wanted.get(id);
            if (current == null || !current.attemptAt().equals(session.connection.attemptAt())) session.close();
        });
        for (var connection : connections) {
            if (sessions.containsKey(connection.coordinatorId()) || retries.getOrDefault(connection.coordinatorId(), 0L) > System.currentTimeMillis()) continue;
            var session = new Session(connection);
            if (sessions.putIfAbsent(connection.coordinatorId(), session) == null) Thread.ofVirtual().name("pushok-" + connection.publicId()).start(session);
        }
        retries.keySet().removeIf(id -> !wanted.containsKey(id));
    }

    @PreDestroy public void stop() { stopping = true; sessions.values().forEach(Session::close); }

    private record Command(String relativeTopic, JsonNode payload, long receivedAt) { }
    private final class Session implements Runnable, AutoCloseable {
        private final PushokConnection connection;
        private final ArrayBlockingQueue<Command> commands = new ArrayBlockingQueue<>(4);
        private final Map<String, PushokDevice> devices = new LinkedHashMap<>();
        private final Map<String, JsonNode> adapters = new HashMap<>();
        private volatile boolean active = true;
        private volatile PushokCloudClient cloud;
        private volatile MqttClient mqtt;
        private volatile Thread worker;
        private long nextRefresh;
        private long nextPing;
        private boolean authenticated;

        Session(PushokConnection connection) { this.connection = connection; }
        @Override public void run() {
            worker = Thread.currentThread();
            try {
                if (!active || stopping) return;
                var secrets = credentials.decrypt(connection.publicId(), connection.encryptedCredentials());
                cloud = new PushokCloudClient(mapper, settings);
                cloud.connect(connection.hubId(), secrets, connection.hubPublicKey(), "PAIRING".equals(connection.status()));
                if (!facade.reportPushokPairing(connection.coordinatorId(), connection.attemptAt(), cloud.hubPublicKey(), null)) return;
                authenticated = true;
                if (!active || stopping) return;
                connectMqtt(secrets);
                refresh();
                publish("bridge/info", Map.of("version", "PushOk cloud", "permit_join", false, "transport", "PUSHOK_CLOUD"));
                publish("bridge/state", Map.of("state", "online"));
                while (active && cloud.isOpen() && mqtt.isConnected() && !stopping) {
                    JsonNode event;
                    while ((event = cloud.pollEvent()) != null) handleEvent(event);
                    Command command;
                    while ((command = commands.poll()) != null) handleCommand(command);
                    if (System.currentTimeMillis() >= nextRefresh) {
                        refresh();
                        publish("bridge/state", Map.of("state", "online"));
                    }
                    if (System.currentTimeMillis() >= nextPing) { cloud.ping(); nextPing = System.currentTimeMillis() + settings.getReconcileSeconds() * 1000L; }
                    Thread.sleep(100);
                }
            } catch (Exception error) {
                String code = error instanceof PushokCloudClient.Failure failure ? failure.code() : "CLOUD_UNAVAILABLE";
                log.warn("PushOk {}: {}", connection.publicId(), code);
                if ((!authenticated && "PAIRING".equals(connection.status())) || !"CLOUD_UNAVAILABLE".equals(code))
                    facade.reportPushokPairing(connection.coordinatorId(), connection.attemptAt(), null, code);
            } finally {
                close();
                retries.put(connection.coordinatorId(), System.currentTimeMillis() + settings.getRetrySeconds() * 1000L);
                sessions.remove(connection.coordinatorId(), this);
            }
        }

        private void connectMqtt(PushokCredentials.Secrets secrets) throws Exception {
            if (!connection.mqttServer().startsWith("mqtts://")) throw new IllegalStateException("MQTTS required");
            mqtt = new MqttClient(connection.mqttServer().replaceFirst("^mqtts://", "ssl://"), connection.mqttUsername(), new MemoryPersistence());
            mqtt.setTimeToWait(settings.getRequestTimeoutSeconds() * 1000L);
            var options = new MqttConnectOptions();
            options.setCleanSession(true); options.setAutomaticReconnect(false);
            options.setConnectionTimeout(settings.getRequestTimeoutSeconds());
            options.setSocketFactory(SSLSocketFactory.getDefault());
            options.setHttpsHostnameVerificationEnabled(true);
            options.setUserName(connection.mqttUsername()); options.setPassword(secrets.mqttPassword().toCharArray());
            options.setWill(connection.baseTopic() + "/bridge/state", "{\"state\":\"offline\"}".getBytes(StandardCharsets.UTF_8), 1, true);
            mqtt.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) { active = false; }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
                @Override public void messageArrived(String topic, MqttMessage message) throws Exception {
                    if (!active || message.isRetained() || !topic.startsWith(connection.baseTopic() + "/")
                            || message.getPayload().length > settings.getMaxMessageBytes()) return;
                    String relative = topic.substring(connection.baseTopic().length() + 1);
                    if (relative.startsWith("bridge/request/") || (relative.endsWith("/set") && relative.chars().filter(c -> c == '/').count() == 1)) {
                        JsonNode payload = mapper.readTree(message.getPayload());
                        if (!commands.offer(new Command(relative, payload, System.currentTimeMillis())))
                            log.warn("PushOk {}: ochered komand zapolnena", connection.publicId());
                    }
                }
            });
            mqtt.connect(options);
            mqtt.subscribe(new String[] { connection.baseTopic() + "/+/set", connection.baseTopic() + "/bridge/request/#" }, new int[] { 0, 0 });
        }

        private void refresh() throws Exception {
            JsonNode list = cloud.request("listObjects", Map.of("type", "zigbee"));
            if (!list.isArray()) throw new PushokCloudClient.Failure("HUB_PROTOCOL_ERROR");
            var current = new LinkedHashMap<String, PushokDevice>();
            var names = new HashSet<String>();
            for (JsonNode description : list) {
                String id = description.path("id").asText();
                if (!id.matches("[A-Fa-f0-9]{16}")) continue;
                var params = Map.of("id", id, "type", "zigbee");
                JsonNode attributes = cloud.request("getAttributes", params);
                String name = PushokDevice.name(attributes, id, mapper);
                if (!names.add(name)) { name += " " + id; names.add(name); }
                String driver = description.path("drv").asText();
                String adapterKey = driver + ":" + description.path("adptr-crc").asText();
                JsonNode adapter = adapters.get(adapterKey);
                if (adapter == null) {
                    JsonNode raw = cloud.request("getAdapter", Map.of("drv", driver));
                    adapter = raw.path("content").isTextual() ? mapper.readTree(raw.path("content").asText()) : raw;
                    adapters.put(adapterKey, adapter);
                }
                var device = new PushokDevice(description, name, adapter);
                device.update(cloud.request("getState", params));
                current.put(id, device);
            }
            var metadata = current.values().stream().map(device -> device.metadata(selfService.getWritableProperties())).toList();
            publish("bridge/devices", metadata);
            for (var device : current.values()) {
                var previous = devices.get(device.id());
                publish(device.friendlyName() + "/availability", Map.of("state", device.offline() ? "offline" : "online"));
                if (previous == null || !previous.state().equals(device.state()) || !previous.timestamps().equals(device.timestamps())
                        || !previous.friendlyName().equals(device.friendlyName()))
                    publish(device.friendlyName(), device.state());
            }
            devices.clear(); devices.putAll(current);
            nextRefresh = System.currentTimeMillis() + settings.getRefreshSeconds() * 1000L;
        }

        private void handleEvent(JsonNode event) {
            String type = event.path("evt").asText();
            if ("object_add".equals(type) || "object_remove".equals(type)) { nextRefresh = 0; return; }
            var device = devices.get(event.path("id").asText());
            if (device == null || !"object_update".equals(type)) return;
            boolean wasOffline = device.offline();
            if (device.update(event.path("props"))) publish(device.friendlyName(), device.state());
            if (wasOffline != device.offline()) publish(device.friendlyName() + "/availability", Map.of("state", device.offline() ? "offline" : "online"));
        }

        private void handleCommand(Command command) {
            String response = "bridge/response/device/set";
            try {
                if (!active || System.currentTimeMillis() - command.receivedAt() > settings.getRequestTimeoutSeconds() * 1000L)
                    throw new IllegalArgumentException("COMMAND_EXPIRED");
                if (command.relativeTopic().startsWith("bridge/request/")) {
                    response = command.relativeTopic().replace("bridge/request/", "bridge/response/");
                    throw new IllegalArgumentException("USE_UPRAVLYATOR");
                }
                String name = command.relativeTopic().substring(0, command.relativeTopic().length() - 4);
                var device = devices.values().stream().filter(item -> item.friendlyName().equals(name)).findFirst().orElseThrow();
                if (!command.payload().isObject() || command.payload().size() != 1) throw new IllegalArgumentException("ONE_PROPERTY_REQUIRED");
                var field = command.payload().fields().next();
                if (!selfService.getWritableProperties().contains(field.getKey())) throw new IllegalArgumentException("PROPERTY_NOT_ALLOWED");
                JsonNode result = cloud.request("setState", device.command(field.getKey(), field.getValue()));
                if (!result.asBoolean(false)) throw new IllegalArgumentException("HUB_COMMAND_REJECTED");
                publish(response, Map.of("status", "ok", "data", Map.of("id", device.ieee(), "property", field.getKey(), "accepted", true)), false);
            } catch (Exception error) {
                publish(response, Map.of("status", "error", "error", command.relativeTopic().startsWith("bridge/request/")
                        ? "Добавляйте и переименовывайте устройства в Управляторе" : "Команда не подтверждена; проверьте состояние устройства"), false);
            }
        }
        private void publish(String topic, Object value) { publish(topic, value, true); }
        private void publish(String topic, Object value, boolean retained) {
            try {
                var message = new MqttMessage(mapper.writeValueAsBytes(value)); message.setQos(1); message.setRetained(retained);
                mqtt.publish(connection.baseTopic() + "/" + topic, message);
            } catch (Exception error) { throw new PushokCloudClient.Failure("CLOUD_UNAVAILABLE"); }
        }
        @Override public synchronized void close() {
            active = false; commands.clear();
            if (mqtt != null && mqtt.isConnected()) {
                try { publish("bridge/state", Map.of("state", "offline")); mqtt.disconnect(); } catch (Exception ignored) { }
            }
            if (mqtt != null) { try { mqtt.close(); } catch (Exception ignored) { } }
            if (cloud != null) cloud.close();
            if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        }
    }
}
