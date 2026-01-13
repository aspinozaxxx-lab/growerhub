package ru.growerhub.backend.firmware.contract;

public interface FirmwareUpdateGateway {
    void publishOta(String deviceId, String firmwareUrl, String version, String sha256);
}
