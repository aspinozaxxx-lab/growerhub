package ru.growerhub.backend.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;

public class PahoMqttSubscriber implements MqttSubscriber, SmartLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(PahoMqttSubscriber.class);

    private final MqttSettings settings;
    private final DebugSettings debugSettings;
    private final MqttTopicSettings topicSettings;
    private final MqttMessageHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MqttClient client;

    public PahoMqttSubscriber(
            MqttSettings settings,
            DebugSettings debugSettings,
            MqttTopicSettings topicSettings,
            MqttMessageHandler handler
    ) {
        this.settings = settings;
        this.debugSettings = debugSettings;
        this.topicSettings = topicSettings;
        this.handler = handler;
    }

    @Override
    public void start() {
        if (running.get()) {
            return;
        }
        String clientId = settings.getClientIdPrefix() + "-sub-" + UUID.randomUUID().toString().replace("-", "");
        try {
            client = new MqttClient(buildBrokerUrl(), clientId, new MemoryPersistence());
            client.setCallback(new SubscriberCallback());
            MqttConnectOptions options = buildConnectOptions();
            client.connect(options);
            subscribeAll();
            running.set(true);
            logger.info("MQTT subscriber connected to {}:{} as {}", settings.getHost(), settings.getPort(), clientId);
        } catch (MqttException ex) {
            running.set(false);
            logger.warn("MQTT subscriber failed to start: {}", ex.getMessage());
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
            logger.warn("MQTT subscriber disconnect failed: {}", ex.getMessage());
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

    private void subscribeAll() throws MqttException {
        List<String> topics = new ArrayList<>();
        String listenTopic = topicSettings.getListen();
        if (listenTopic != null && !listenTopic.isBlank()) {
            topics.add(listenTopic);
        } else if (!topicSettings.getListenTopics().isEmpty()) {
            topicSettings.getListenTopics().stream()
                    .filter(topic -> topic != null && !topic.isBlank())
                    .forEach(topics::add);
        } else {
            topics.add(topicSettings.getState());
            topics.add(topicSettings.getAck());
            topics.add(topicSettings.getEvents());
        }
        if (topics.isEmpty()) {
            throw new MqttException(new IllegalStateException("MQTT subscription topics are empty"));
        }

        String[] topicArray = topics.toArray(String[]::new);
        int[] qos = topics.stream().mapToInt(ignored -> 1).toArray();
        client.subscribe(topicArray, qos);
        topics.forEach(topic -> logger.info("mqtt: subscribed to {} qos=1", topic));
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

    private class SubscriberCallback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                try {
                    subscribeAll();
                } catch (MqttException ex) {
                    logger.warn("mqtt: resubscribe failed: {}", ex.getMessage());
                }
            }
            if (debugSettings.isDebug()) {
                logger.info("MQTT DEBUG subscriber connectComplete reconnect={}", reconnect);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            logger.warn("mqtt: connection lost: {}", cause != null ? cause.getMessage() : "unknown");
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            byte[] payload = message != null ? message.getPayload() : new byte[0];
            if (debugSettings.isDebug()) {
                logger.info("MQTT DEBUG message topic={} payload={}", topic, new String(payload, StandardCharsets.UTF_8));
            }
            try {
                handler.handleInboundMessage(topic, payload);
            } catch (Exception ex) {
                logger.warn("MQTT handler failure for topic {}: {}", topic, ex.getMessage());
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
