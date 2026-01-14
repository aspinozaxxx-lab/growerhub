﻿﻿package ru.growerhub.backend.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FirmwareInitializer {
    private static final Logger logger = LoggerFactory.getLogger(FirmwareInitializer.class);

    private final FirmwareSettings firmwareSettings;

    public FirmwareInitializer(FirmwareSettings firmwareSettings) {
        this.firmwareSettings = firmwareSettings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureFirmwareDir() {
        Path firmwareDir = firmwareSettings.getFirmwareDir();
        try {
            Files.createDirectories(firmwareDir);
        } catch (IOException ex) {
            logger.warn("Ne udalos sozdat firmware dir {}: {}", firmwareDir, ex.getMessage());
        }
    }
}
