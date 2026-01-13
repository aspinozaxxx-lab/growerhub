package ru.growerhub.backend.firmware.contract;

public record FirmwareUploadResult(String status, String version, String path) {
}
