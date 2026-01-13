package ru.growerhub.backend.firmware.contract;

public record FirmwareTriggerResult(String status, String version, String url, String sha256) {
}
