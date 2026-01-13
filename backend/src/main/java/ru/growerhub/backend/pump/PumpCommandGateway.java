package ru.growerhub.backend.pump;

import java.time.LocalDateTime;

public interface PumpCommandGateway {
    void publishStart(String deviceId, String correlationId, LocalDateTime startedAt, Integer durationS);

    void publishStop(String deviceId, String correlationId, LocalDateTime issuedAt);

    void publishReboot(String deviceId, String correlationId, long issuedAt);

    PumpAck getAck(String correlationId);
}

