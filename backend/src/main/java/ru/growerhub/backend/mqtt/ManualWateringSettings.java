package ru.growerhub.backend.mqtt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ManualWateringSettings {
    private final String mqttHost;
    private final int mqttPort;
    private final String mqttUsername;
    private final boolean mqttTls;
    private final boolean debug;
    private final int deviceOnlineThresholdSeconds;

    public ManualWateringSettings(
            @Value("${MQTT_HOST:localhost}") String mqttHost,
            @Value("${MQTT_PORT:1883}") int mqttPort,
            @Value("${MQTT_USERNAME:}") String mqttUsername,
            @Value("${MQTT_TLS:false}") boolean mqttTls,
            @Value("${DEBUG:true}") boolean debug,
            @Value("${DEVICE_ONLINE_THRESHOLD_S:60}") int deviceOnlineThresholdSeconds
    ) {
        this.mqttHost = mqttHost;
        this.mqttPort = mqttPort;
        this.mqttUsername = mqttUsername != null && !mqttUsername.isBlank() ? mqttUsername : null;
        this.mqttTls = mqttTls;
        this.debug = debug;
        this.deviceOnlineThresholdSeconds = deviceOnlineThresholdSeconds;
    }

    public String getMqttHost() {
        return mqttHost;
    }

    public int getMqttPort() {
        return mqttPort;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public boolean isMqttTls() {
        return mqttTls;
    }

    public boolean isDebug() {
        return debug;
    }

    public int getDeviceOnlineThresholdSeconds() {
        return deviceOnlineThresholdSeconds;
    }
}
