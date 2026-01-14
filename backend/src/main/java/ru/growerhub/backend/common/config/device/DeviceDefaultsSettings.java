package ru.growerhub.backend.common.config.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki umolchanij dlya device.
@ConfigurationProperties(prefix = "device.defaults")
public class DeviceDefaultsSettings {
    private double targetMoisture = 40.0;
    private int wateringDurationSeconds = 30;
    private int wateringTimeoutSeconds = 300;
    private int lightOnHour = 6;
    private int lightOffHour = 22;
    private int lightDurationHours = 16;
    private String currentVersion = "1.0.0";
    private String latestVersion = "1.0.0";
    private boolean updateAvailable = false;

    public double getTargetMoisture() {
        return targetMoisture;
    }

    public void setTargetMoisture(double targetMoisture) {
        this.targetMoisture = targetMoisture;
    }

    public int getWateringDurationSeconds() {
        return wateringDurationSeconds;
    }

    public void setWateringDurationSeconds(int wateringDurationSeconds) {
        this.wateringDurationSeconds = wateringDurationSeconds;
    }

    public int getWateringTimeoutSeconds() {
        return wateringTimeoutSeconds;
    }

    public void setWateringTimeoutSeconds(int wateringTimeoutSeconds) {
        this.wateringTimeoutSeconds = wateringTimeoutSeconds;
    }

    public int getLightOnHour() {
        return lightOnHour;
    }

    public void setLightOnHour(int lightOnHour) {
        this.lightOnHour = lightOnHour;
    }

    public int getLightOffHour() {
        return lightOffHour;
    }

    public void setLightOffHour(int lightOffHour) {
        this.lightOffHour = lightOffHour;
    }

    public int getLightDurationHours() {
        return lightDurationHours;
    }

    public void setLightDurationHours(int lightDurationHours) {
        this.lightDurationHours = lightDurationHours;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public void setUpdateAvailable(boolean updateAvailable) {
        this.updateAvailable = updateAvailable;
    }
}
