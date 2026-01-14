﻿﻿package ru.growerhub.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firmware")
public class FirmwareSettings {
    private String binariesDir = "server/firmware_binaries";

    public Path getFirmwareDir() {
        return Paths.get(binariesDir).toAbsolutePath().normalize();
    }

    public String getBinariesDir() {
        return binariesDir;
    }

    public void setBinariesDir(String binariesDir) {
        this.binariesDir = binariesDir;
    }
}
