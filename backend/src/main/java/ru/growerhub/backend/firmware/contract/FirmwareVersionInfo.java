package ru.growerhub.backend.firmware.contract;

import java.time.Instant;

public record FirmwareVersionInfo(
        String version,
        String hardwareProfile,
        long size,
        String sha256,
        Instant mtime
) {
}
