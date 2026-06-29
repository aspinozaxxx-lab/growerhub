package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeMqttSnapshotMessage(
        ZigbeeMqttMessageType type,
        String topic,
        String relativeTopic,
        String friendlyName,
        String rawPayload,
        Object payload,
        LocalDateTime receivedAt
) {
}
