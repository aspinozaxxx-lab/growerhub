package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class PahoMqttPublisher implements MqttPublisher, SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(PahoMqttPublisher.class);

    private final MqttSettings settings;
    private final DebugSettings debugSettings;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MqttClient client;

    public PahoMqttPublisher(MqttSettings settings, DebugSettings debugSettings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.debugSettings = debugSettings;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishCmd(String deviceId, Object cmd) {
        if (!isRunning()) {
            throw new IllegalStateException("MQTT client is not connected");
        }
        String topic = "gh/dev/" + deviceId + "/cmd";
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(cmd);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize MQTT command", ex);
        }
        MqttMessage message = new MqttMessage(payload);
        message.setQos(1);
        message.setRetained(false);
        try {
            client.publish(topic, message);
        } catch (MqttException ex) {
            throw new IllegalStateException("Failed to publish MQTT command", ex);
        }
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG publish topic={} payload={}", topic, new String(payload, StandardCharsets.UTF_8));
        }
    }

    @Override
    public void start() {
        if (running.get()) {
            return;
        }
        String clientId = settings.getClientIdPrefix() + "-" + UUID.randomUUID().toString().replace("-", "");
        try {
            client = new MqttClient(buildBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = buildConnectOptions();
            client.connect(options);
            running.set(true);
            logger.info("MQTT publisher connected to {}:{} as {}", settings.getHost(), settings.getPort(), clientId);
        } catch (MqttException ex) {
            running.set(false);
            logger.warn("MQTT publisher failed to start: {}", ex.getMessage());
        }
    }

    @Override
    public void stop() {
        if (!running.get()) {
            return;
        }
        try {
            client.disconnect();
        } catch (MqttException ex) {
            logger.warn("MQTT publisher disconnect failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private MqttConnectOptions buildConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (settings.getUsername() != null) {
            options.setUserName(settings.getUsername());
            if (settings.getPassword() != null) {
                options.setPassword(settings.getPassword().toCharArray());
            }
        }
        if (settings.isTls()) {
            options.setSocketFactory(SSLSocketFactory.getDefault());
        }
        return options;
    }

    private String buildBrokerUrl() {
        String scheme = settings.isTls() ? "ssl" : "tcp";
        return scheme + "://" + settings.getHost() + ":" + settings.getPort();
    }
}
