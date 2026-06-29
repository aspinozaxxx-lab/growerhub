package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "automation")
public class AutomationSettings {
    private long workerPeriodMs = 30000;
    private int staleSensorMinutes = 15;
    private int manualOverrideMinutes = 15;
    private String timezone = "Europe/Istanbul";

    public long getWorkerPeriodMs() {
        return workerPeriodMs;
    }

    public void setWorkerPeriodMs(long workerPeriodMs) {
        this.workerPeriodMs = workerPeriodMs;
    }

    public int getStaleSensorMinutes() {
        return staleSensorMinutes;
    }

    public void setStaleSensorMinutes(int staleSensorMinutes) {
        this.staleSensorMinutes = staleSensorMinutes;
    }

    public int getManualOverrideMinutes() {
        return manualOverrideMinutes;
    }

    public void setManualOverrideMinutes(int manualOverrideMinutes) {
        this.manualOverrideMinutes = manualOverrideMinutes;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
