package ru.growerhub.backend.mqtt;

import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.DomainException;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.pump.PumpAck;
import ru.growerhub.backend.pump.PumpCommandGateway;

@Component
public class MqttPumpCommandGateway implements PumpCommandGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final AckStore ackStore;

    public MqttPumpCommandGateway(ObjectProvider<MqttPublisher> publisherProvider, AckStore ackStore) {
        this.publisherProvider = publisherProvider;
        this.ackStore = ackStore;
    }

    @Override
    public void publishStart(String deviceId, String correlationId, LocalDateTime startedAt, Integer durationS) {
        CmdPumpStart cmd = new CmdPumpStart("pump.start", correlationId, startedAt, durationS);
        publishCommand(deviceId, cmd);
    }

    @Override
    public void publishStop(String deviceId, String correlationId, LocalDateTime issuedAt) {
        CmdPumpStop cmd = new CmdPumpStop("pump.stop", correlationId, issuedAt);
        publishCommand(deviceId, cmd);
    }

    @Override
    public void publishReboot(String deviceId, String correlationId, long issuedAt) {
        CmdReboot cmd = new CmdReboot("reboot", correlationId, issuedAt);
        publishCommand(deviceId, cmd);
    }

    @Override
    public PumpAck getAck(String correlationId) {
        ManualWateringAck ack = ackStore.get(correlationId);
        if (ack == null) {
            return null;
        }
        return new PumpAck(ack.correlationId(), ack.result(), ack.reason(), ack.status());
    }

    private void publishCommand(String deviceId, Object cmd) {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new DomainException("unavailable", "MQTT publisher unavailable");
        }
        try {
            publisher.publishCmd(deviceId, cmd);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("bad_gateway", "Failed to publish manual watering command");
        }
    }
}
