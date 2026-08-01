﻿﻿package ru.growerhub.backend.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FirmwareInitializer {
    private static final Logger logger = LoggerFactory.getLogger(FirmwareInitializer.class);
    private static final String BUNDLED_MANIFEST = "firmware-release/manifest.properties";
    private static final String[] HARDWARE_PROFILES = {"esp32dev", "esp32c3_supermini"};

    private final FirmwareSettings firmwareSettings;

    public FirmwareInitializer(FirmwareSettings firmwareSettings) {
        this.firmwareSettings = firmwareSettings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureFirmwareDir() {
        Path firmwareDir = firmwareSettings.getFirmwareDir();
        try {
            Files.createDirectories(firmwareDir);
            publishBundledFirmware(firmwareDir);
        } catch (IOException ex) {
            logger.warn("Ne udalos podgotovit firmware dir {}: {}", firmwareDir, ex.getMessage());
        }
    }

    private void publishBundledFirmware(Path firmwareDir) throws IOException {
        ClassPathResource manifestResource = new ClassPathResource(BUNDLED_MANIFEST);
        if (!manifestResource.exists()) {
            return;
        }

        Properties manifest = new Properties();
        try (InputStream input = manifestResource.getInputStream()) {
            manifest.load(input);
        }
        String version = manifest.getProperty("version", "").trim();
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IOException("invalid bundled firmware manifest");
        }
        for (String hardwareProfile : HARDWARE_PROFILES) {
            publishBundledFirmwareVariant(firmwareDir, manifest, version, hardwareProfile);
        }
    }

    private void publishBundledFirmwareVariant(
            Path firmwareDir,
            Properties manifest,
            String version,
            String hardwareProfile
    ) throws IOException {
        String expectedSha256 = manifest.getProperty(hardwareProfile + ".sha256", "")
                .trim()
                .toLowerCase();
        ClassPathResource binaryResource = new ClassPathResource(
                "firmware-release/" + hardwareProfile + ".bin");
        if (!expectedSha256.matches("[0-9a-f]{64}") || !binaryResource.exists()) {
            throw new IOException("invalid bundled firmware variant " + hardwareProfile);
        }

        Path target = firmwareDir.resolve(version + "." + hardwareProfile + ".bin").normalize();
        if (!target.startsWith(firmwareDir)) {
            throw new IOException("invalid bundled firmware target");
        }
        if (Files.exists(target) && expectedSha256.equals(sha256(target))) {
            return;
        }

        Path temporary = Files.createTempFile(firmwareDir, ".firmware-", ".tmp");
        try {
            try (InputStream input = binaryResource.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!expectedSha256.equals(sha256(temporary))) {
                throw new IOException("bundled firmware sha256 mismatch");
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info(
                    "Firmware release opublikovan version={} profile={}",
                    version,
                    hardwareProfile
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IOException("sha256 unavailable", ex);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
