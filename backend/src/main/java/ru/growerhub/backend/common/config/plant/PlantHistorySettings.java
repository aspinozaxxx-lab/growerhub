package ru.growerhub.backend.common.config.plant;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki istorii rastenij.
@ConfigurationProperties(prefix = "plant.history")
public class PlantHistorySettings {
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
