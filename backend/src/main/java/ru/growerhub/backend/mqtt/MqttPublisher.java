package ru.growerhub.backend.mqtt;

public interface MqttPublisher {
    void publishCmd(String deviceId, Object cmd);

    default void publishJson(String topic, Object payload, int qos, boolean retained) {
        throw new UnsupportedOperationException("generic MQTT publish is unavailable");
    }
}
