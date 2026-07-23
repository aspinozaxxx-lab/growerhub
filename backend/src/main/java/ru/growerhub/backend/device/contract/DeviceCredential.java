package ru.growerhub.backend.device.contract;

import java.time.LocalDateTime;

public record DeviceCredential(
        String deviceId,
        String token,
        LocalDateTime issuedAt
) {
}
