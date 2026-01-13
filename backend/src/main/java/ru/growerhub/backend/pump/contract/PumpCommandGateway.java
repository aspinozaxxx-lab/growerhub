package ru.growerhub.backend.pump.contract;

import java.time.LocalDateTime;
import ru.growerhub.backend.pump.contract.PumpAck;

public interface PumpCommandGateway {
    void publishStart(String deviceId, String correlationId, LocalDateTime startedAt, Integer durationS);

    void publishStop(String deviceId, String correlationId, LocalDateTime issuedAt);

    void publishReboot(String deviceId, String correlationId, long issuedAt);

    PumpAck getAck(String correlationId);
}

