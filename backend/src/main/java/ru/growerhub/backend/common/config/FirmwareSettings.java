﻿﻿package ru.growerhub.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firmware")
public class FirmwareSettings {
    private String binariesDir = "firmware_binaries";
    private int updateTimeoutSeconds = 300;

    public Path getFirmwareDir() {
        return Paths.get(binariesDir).toAbsolutePath().normalize();
    }

    public String getBinariesDir() {
        return binariesDir;
    }

    public void setBinariesDir(String binariesDir) {
        this.binariesDir = binariesDir;
    }

    public int getUpdateTimeoutSeconds() {
        return updateTimeoutSeconds;
    }

    public void setUpdateTimeoutSeconds(int updateTimeoutSeconds) {
        this.updateTimeoutSeconds = updateTimeoutSeconds;
    }
}
