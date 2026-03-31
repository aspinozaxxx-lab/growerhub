package ru.growerhub.backend.device.contract;

import java.time.LocalDateTime;

public record DeviceServiceEventData(
        String type,
        String sensorScope,
        String sensorType,
        Integer channel,
        String failureId,
        String errorCode,
        LocalDateTime eventAt,
        String payloadJson
) {
}
