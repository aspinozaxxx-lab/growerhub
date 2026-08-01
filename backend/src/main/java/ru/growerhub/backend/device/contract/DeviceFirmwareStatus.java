package ru.growerhub.backend.device.contract;

import java.time.LocalDateTime;

public record DeviceFirmwareStatus(
        String currentVersion,
        String hardwareProfile,
        String targetVersion,
        String firmwareUrl,
        DeviceFirmwareUpdateState state,
        String error,
        String correlationId,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        boolean online
) {
}
