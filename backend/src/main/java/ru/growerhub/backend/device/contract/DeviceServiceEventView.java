package ru.growerhub.backend.device.contract;

import java.time.LocalDateTime;

public record DeviceServiceEventView(
        Integer id,
        Integer deviceId,
        DeviceServiceEventType eventType,
        String sensorScope,
        String sensorType,
        Integer channel,
        String failureId,
        String errorCode,
        LocalDateTime eventAt,
        LocalDateTime receivedAt
) {
}
