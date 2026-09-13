package ru.growerhub.backend.mqtt;

import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpCommandGateway;

@Component
public class MqttPumpCommandGateway implements PumpCommandGateway {
    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final AckStore ackStore;

    private final DeviceFacade deviceFacade;
    private final DemoFacade demoFacade;

    public MqttPumpCommandGateway(ObjectProvider<MqttPublisher> publisherProvider, AckStore ackStore,
            @Lazy DeviceFacade deviceFacade, @Lazy DemoFacade demoFacade) {
        this.publisherProvider = publisherProvider;
        this.ackStore = ackStore;
        this.deviceFacade = deviceFacade;
        this.demoFacade = demoFacade;
    }

    @Override
    public void publishStart(String deviceId, String correlationId, LocalDateTime startedAt, Integer durationS) {
        if (simulate(deviceId, correlationId, "start", durationS)) return;
        CmdPumpStart cmd = new CmdPumpStart("pump.start", correlationId, startedAt, durationS);
        publishCommand(deviceId, cmd);
    }

    @Override
    public void publishStop(String deviceId, String correlationId, LocalDateTime issuedAt) {
        if (simulate(deviceId, correlationId, "stop", null)) return;
        CmdPumpStop cmd = new CmdPumpStop("pump.stop", correlationId, issuedAt);
        publishCommand(deviceId, cmd);
    }

    @Override
    public void publishReboot(String deviceId, String correlationId, long issuedAt) {
        if (simulate(deviceId, correlationId, "reboot", null)) return;
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

    private boolean simulate(String deviceId, String correlationId, String action, Integer durationS) {
        if (!deviceFacade.isSimulatedDevice(deviceId)) return false;
        PumpAck ack = demoFacade.commandPump(deviceId, correlationId, action, durationS);
        ackStore.put(deviceId, new ManualWateringAck(ack.correlationId(), ack.result(), ack.reason(), ack.status()));
        return true;
    }

    private void publishCommand(String deviceId, Object cmd) {
        deviceFacade.requirePhysicalTarget(deviceId);
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
