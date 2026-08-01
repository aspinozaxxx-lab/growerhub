package ru.growerhub.backend.firmware.contract;

public record FirmwareCheckResult(
        boolean updateAvailable,
        String currentVersion,
        String hardwareProfile,
        String latestVersion,
        String targetVersion,
        String url,
        ru.growerhub.backend.device.contract.DeviceFirmwareUpdateState state,
        String error,
        java.time.LocalDateTime requestedAt,
        java.time.LocalDateTime completedAt,
        boolean online
) {
}
