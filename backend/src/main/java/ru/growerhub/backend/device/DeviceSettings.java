package ru.growerhub.backend.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "device")
public class DeviceSettings {
    private int onlineThresholdS = 60;

    public int getOnlineThresholdS() {
        return onlineThresholdS;
    }

    public void setOnlineThresholdS(int onlineThresholdS) {
        this.onlineThresholdS = onlineThresholdS;
    }
}
