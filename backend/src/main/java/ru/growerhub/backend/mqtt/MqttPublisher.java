package ru.growerhub.backend.mqtt;

public interface MqttPublisher {
    void publishCmd(String deviceId, Object cmd);
}
