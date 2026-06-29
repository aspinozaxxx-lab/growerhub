package ru.growerhub.backend.zigbee.contract;

public record ZigbeeCommandPublishResult(
        String message,
        String topic
) {
}
