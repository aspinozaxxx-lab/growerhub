package ru.growerhub.backend.common.config.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "device.claim")
public class DeviceClaimSettings {
    private int initialFailureLimit = 10;
    private int blockSeconds = 3600;
    private int restrictedWindowSeconds = 3600;

    public int getInitialFailureLimit() {
        return initialFailureLimit;
    }

    public void setInitialFailureLimit(int initialFailureLimit) {
        this.initialFailureLimit = initialFailureLimit;
    }

    public int getBlockSeconds() {
        return blockSeconds;
    }

    public void setBlockSeconds(int blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public int getRestrictedWindowSeconds() {
        return restrictedWindowSeconds;
    }

    public void setRestrictedWindowSeconds(int restrictedWindowSeconds) {
        this.restrictedWindowSeconds = restrictedWindowSeconds;
    }
}
