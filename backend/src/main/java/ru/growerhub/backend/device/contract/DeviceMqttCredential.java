package ru.growerhub.backend.device.contract;

import java.time.LocalDateTime;

public record DeviceMqttCredential(
        String deviceId,
        String host,
        int port,
        boolean tls,
        String username,
        String password,
        String clientId,
        LocalDateTime provisionedAt
) {
}
