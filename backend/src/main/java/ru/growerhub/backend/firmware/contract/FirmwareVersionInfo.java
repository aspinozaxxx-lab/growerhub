package ru.growerhub.backend.firmware.contract;

import java.time.Instant;

public record FirmwareVersionInfo(
        String version,
        long size,
        String sha256,
        Instant mtime
) {
}