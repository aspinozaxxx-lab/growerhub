package ru.growerhub.backend.mqtt;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.firmware.contract.FirmwareUpdateGateway;
import ru.growerhub.backend.mqtt.model.CmdOta;

@Component
public class MqttFirmwareUpdateGateway implements FirmwareUpdateGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;

    public MqttFirmwareUpdateGateway(ObjectProvider<MqttPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    @Override
    public void publishOta(String deviceId, String firmwareUrl, String version, String sha256) {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new DomainException("unavailable", "MQTT publisher unavailable");
        }
        CmdOta cmd = new CmdOta("ota", firmwareUrl, version, sha256);
        try {
            publisher.publishCmd(deviceId, cmd);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("unavailable", "mqtt publish failed");
        }
    }
}
