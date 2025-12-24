package ru.growerhub.backend.mqtt;

public class ManualInjectorSubscriber implements MqttSubscriber {
    private final MqttMessageHandler handler;
    private boolean running;

    public ManualInjectorSubscriber(MqttMessageHandler handler) {
        this.handler = handler;
    }

    public void injectState(String topic, byte[] payload) {
        handler.handleStateMessage(topic, payload);
    }

    public void injectAck(String topic, byte[] payload) {
        handler.handleAckMessage(topic, payload);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
