package ru.growerhub.backend.mqtt;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;

@Component
public class MqttZigbeeCommandGateway implements ZigbeeCommandGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final ZigbeeFacade zigbeeFacade;
    private final DemoFacade demoFacade;

    public MqttZigbeeCommandGateway(ObjectProvider<MqttPublisher> publisherProvider,
            @Lazy ZigbeeFacade zigbeeFacade, @Lazy DemoFacade demoFacade) {
        this.publisherProvider = publisherProvider;
        this.zigbeeFacade = zigbeeFacade;
        this.demoFacade = demoFacade;
    }

    @Override
    public void publishPermitJoin(String baseTopic, int seconds) {
        zigbeeFacade.requirePhysicalBaseTopic(baseTopic);
        publish(bridgeTopic(baseTopic, "request/permit_join"), Map.of("time", seconds));
    }

    @Override
    public void publishSet(String baseTopic, String friendlyName, Map<String, Object> payload) {
        if (zigbeeFacade.isSimulatedBaseTopic(baseTopic)) {
            demoFacade.commandZigbee(baseTopic, friendlyName, payload);
            return;
        }
        zigbeeFacade.requirePhysicalBaseTopic(baseTopic);
        publish(baseTopic + "/" + friendlyName + "/set", payload);
    }

    @Override
    public void publishRename(String baseTopic, String fromFriendlyName, String toFriendlyName) {
        if (zigbeeFacade.isSimulatedBaseTopic(baseTopic)) {
            demoFacade.renameZigbee(baseTopic, fromFriendlyName, toFriendlyName);
            return;
        }
        zigbeeFacade.requirePhysicalBaseTopic(baseTopic);
        publish(bridgeTopic(baseTopic, "request/device/rename"), Map.of(
                "from", fromFriendlyName,
                "to", toFriendlyName,
                "homeassistant_rename", false
        ));
    }

    @Override
    public void publishWateringStart(String baseTopic, String friendlyName, Map<String, Object> payload) {
        if (zigbeeFacade.isSimulatedBaseTopic(baseTopic)) {
            demoFacade.commandZigbee(baseTopic, friendlyName, payload);
            return;
        }
        zigbeeFacade.requirePhysicalBaseTopic(baseTopic);
        // Start ne povtoryaetsya MQTT-klientom; OFF ostayotsya s QoS 1.
        publish(baseTopic + "/" + friendlyName + "/set", payload, 0);
    }

    private void publish(String topic, Object payload) {
        publish(topic, payload, 1);
    }

    private void publish(String topic, Object payload, int qos) {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new DomainException("unavailable", "MQTT publisher unavailable");
        }
        try {
            publisher.publishJson(topic, payload, qos, false);
        } catch (Exception ex) {
            throw new DomainException("bad_gateway", "Failed to publish Zigbee MQTT command");
        }
    }

    private String bridgeTopic(String baseTopic, String relative) {
        return baseTopic + "/bridge/" + relative;
    }
}
