package ru.growerhub.backend.firmware;

import java.time.Instant;

public record FirmwareVersionInfo(
        String version,
        long size,
        String sha256,
        Instant mtime
) {
}