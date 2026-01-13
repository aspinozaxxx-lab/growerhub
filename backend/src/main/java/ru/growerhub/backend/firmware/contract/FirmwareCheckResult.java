package ru.growerhub.backend.firmware.contract;

public record FirmwareCheckResult(boolean updateAvailable, String version, String url) {
}
