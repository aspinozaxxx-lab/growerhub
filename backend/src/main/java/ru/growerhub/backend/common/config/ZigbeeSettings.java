package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zigbee")
public class ZigbeeSettings {
    private int permitJoinDefaultSeconds = 254;

    public int getPermitJoinDefaultSeconds() {
        return permitJoinDefaultSeconds;
    }

    public void setPermitJoinDefaultSeconds(int permitJoinDefaultSeconds) {
        this.permitJoinDefaultSeconds = permitJoinDefaultSeconds;
    }
}
