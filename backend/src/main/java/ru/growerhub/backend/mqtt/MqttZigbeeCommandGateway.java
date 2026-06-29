package ru.growerhub.backend.mqtt;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;

@Component
public class MqttZigbeeCommandGateway implements ZigbeeCommandGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final MqttTopicSettings topicSettings;

    public MqttZigbeeCommandGateway(ObjectProvider<MqttPublisher> publisherProvider, MqttTopicSettings topicSettings) {
        this.publisherProvider = publisherProvider;
        this.topicSettings = topicSettings;
    }

    @Override
    public void publishPermitJoin(int seconds) {
        publish(bridgeTopic("request/permit_join"), Map.of("time", seconds));
    }

    @Override
    public void publishSetState(String friendlyName, String state) {
        publish(topicSettings.getZigbeeBase() + "/" + friendlyName + "/set", Map.of("state", state));
    }

    @Override
    public void publishRename(String fromFriendlyName, String toFriendlyName) {
        publish(bridgeTopic("request/device/rename"), Map.of(
                "from", fromFriendlyName,
                "to", toFriendlyName,
                "homeassistant_rename", false
        ));
    }

    private void publish(String topic, Object payload) {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new DomainException("unavailable", "MQTT publisher unavailable");
        }
        try {
            publisher.publishJson(topic, payload, 1, false);
        } catch (Exception ex) {
            throw new DomainException("bad_gateway", "Failed to publish Zigbee MQTT command");
        }
    }

    private String bridgeTopic(String relative) {
        return topicSettings.getZigbeeBase() + "/bridge/" + relative;
    }
}
