package ru.growerhub.backend.common.config.zigbee;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki istorii Zigbee-svojstv.
@ConfigurationProperties(prefix = "zigbee.history")
public class ZigbeeHistorySettings {
    private int maxPoints = 200;
    private int defaultHours = 24;

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int getDefaultHours() {
        return defaultHours;
    }

    public void setDefaultHours(int defaultHours) {
        this.defaultHours = defaultHours;
    }
}
