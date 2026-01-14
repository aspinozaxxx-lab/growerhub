package ru.growerhub.backend.common.config.pump;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki poliva nasosom.
@ConfigurationProperties(prefix = "pump.watering")
public class PumpWateringSettings {
    private int defaultRateMlPerHour = 2000;

    public int getDefaultRateMlPerHour() {
        return defaultRateMlPerHour;
    }

    public void setDefaultRateMlPerHour(int defaultRateMlPerHour) {
        this.defaultRateMlPerHour = defaultRateMlPerHour;
    }
}
