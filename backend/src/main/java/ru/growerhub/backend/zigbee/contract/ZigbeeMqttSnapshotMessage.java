package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeMqttSnapshotMessage(
        String mqttUsername,
        String baseTopic,
        ZigbeeMqttMessageType type,
        String topic,
        String relativeTopic,
        String friendlyName,
        String rawPayload,
        Object payload,
        LocalDateTime receivedAt,
        boolean retained
) {
    public ZigbeeMqttSnapshotMessage(String mqttUsername, String baseTopic, ZigbeeMqttMessageType type,
            String topic, String relativeTopic, String friendlyName, String rawPayload, Object payload,
            LocalDateTime receivedAt) {
        this(mqttUsername, baseTopic, type, topic, relativeTopic, friendlyName, rawPayload, payload, receivedAt, false);
    }
    public ZigbeeMqttSnapshotMessage(
            ZigbeeMqttMessageType type,
            String topic,
            String relativeTopic,
            String friendlyName,
            String rawPayload,
            Object payload,
            LocalDateTime receivedAt
    ) {
        this(null, null, type, topic, relativeTopic, friendlyName, rawPayload, payload, receivedAt);
    }
}
