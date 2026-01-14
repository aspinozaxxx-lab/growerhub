package ru.growerhub.backend.common.config.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki firmware dlya device.
@ConfigurationProperties(prefix = "device.firmware")
public class DeviceFirmwareSettings {
    private String defaultVersion = "old";

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }
}
