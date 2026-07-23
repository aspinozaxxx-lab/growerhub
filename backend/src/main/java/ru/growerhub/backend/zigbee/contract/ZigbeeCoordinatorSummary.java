package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;
import java.util.UUID;

public record ZigbeeCoordinatorSummary(
        UUID id,
        String name,
        String mqttUsername,
        String baseTopic,
        ZigbeeCoordinatorStatus status,
        int deviceCount,
        LocalDateTime lastSeenAt,
        LocalDateTime connectedAt,
        LocalDateTime firstDeviceSeenAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
