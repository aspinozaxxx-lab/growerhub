package ru.growerhub.backend.common.config.pump;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki istorii sostoyaniya nasosa.
@ConfigurationProperties(prefix = "pump.history")
public class PumpHistorySettings {
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
