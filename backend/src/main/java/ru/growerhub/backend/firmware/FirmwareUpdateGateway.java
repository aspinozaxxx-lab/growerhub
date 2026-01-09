package ru.growerhub.backend.firmware;

public interface FirmwareUpdateGateway {
    void publishOta(String deviceId, String firmwareUrl, String version, String sha256);
}
