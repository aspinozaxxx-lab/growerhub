package ru.growerhub.backend.common.config.sensor;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki istorii sensorov.
@ConfigurationProperties(prefix = "sensor.history")
public class SensorHistorySettings {
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
