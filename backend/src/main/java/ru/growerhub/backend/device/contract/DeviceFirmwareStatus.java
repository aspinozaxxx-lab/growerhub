package ru.growerhub.backend.device.contract;

public record DeviceFirmwareStatus(
        Boolean updateAvailable,
        String latestVersion,
        String firmwareUrl
) {
}
