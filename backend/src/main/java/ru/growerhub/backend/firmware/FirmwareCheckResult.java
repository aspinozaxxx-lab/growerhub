package ru.growerhub.backend.firmware;

public record FirmwareCheckResult(boolean updateAvailable, String version, String url) {
}
