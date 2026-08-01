package ru.growerhub.backend.device.contract;

public interface DeviceBrokerCredentialGateway {
    void provisionDevice(String deviceId, String password, String roleName);

    void rotateDevice(String deviceId, String password, String roleName);

    void reconcileDeviceAccess(String deviceId, String roleName);

    void revokeDevice(String deviceId, String roleName);
}
