package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeBridgeData(
        String baseTopic,
        String state,
        Object info,
        Boolean permitJoin,
        Long permitJoinEnd,
        String version,
        LocalDateTime updatedAt
) {
}
