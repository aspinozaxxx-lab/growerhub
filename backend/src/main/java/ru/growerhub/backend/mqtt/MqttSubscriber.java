package ru.growerhub.backend.mqtt;

public interface MqttSubscriber {
    void start();

    void stop();

    boolean isRunning();
}
