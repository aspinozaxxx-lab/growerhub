package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeCommandResponseData(
        String topic,
        String status,
        String error,
        Object response,
        LocalDateTime updatedAt
) {
}
