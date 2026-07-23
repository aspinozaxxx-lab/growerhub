package ru.growerhub.backend.mqtt;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;

@Component
public class MqttZigbeeCommandGateway implements ZigbeeCommandGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;
    public MqttZigbeeCommandGateway(ObjectProvider<MqttPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    @Override
    public void publishPermitJoin(String baseTopic, int seconds) {
        publish(bridgeTopic(baseTopic, "request/permit_join"), Map.of("time", seconds));
    }

    @Override
    public void publishSet(String baseTopic, String friendlyName, Map<String, Object> payload) {
        publish(baseTopic + "/" + friendlyName + "/set", payload);
    }

    @Override
    public void publishRename(String baseTopic, String fromFriendlyName, String toFriendlyName) {
        publish(bridgeTopic(baseTopic, "request/device/rename"), Map.of(
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

    private String bridgeTopic(String baseTopic, String relative) {
        return baseTopic + "/bridge/" + relative;
    }
}
