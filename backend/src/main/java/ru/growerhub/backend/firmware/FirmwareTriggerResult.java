package ru.growerhub.backend.firmware;

public record FirmwareTriggerResult(String status, String version, String url, String sha256) {
}
