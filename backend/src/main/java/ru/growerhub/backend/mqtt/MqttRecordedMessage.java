package ru.growerhub.backend.mqtt;

import java.time.LocalDateTime;

public record MqttRecordedMessage(
        long id,
        LocalDateTime receivedAt,
        String direction,
        String topic,
        String sender,
        String kind,
        String payload
) {
}
