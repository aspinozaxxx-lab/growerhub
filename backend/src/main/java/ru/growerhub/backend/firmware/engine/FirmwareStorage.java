﻿package ru.growerhub.backend.firmware.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.config.FirmwareSettings;
import ru.growerhub.backend.firmware.contract.FirmwareHardwareProfile;
import ru.growerhub.backend.firmware.contract.FirmwareVersionInfo;

@Component
public class FirmwareStorage {
    private static final int READ_BUFFER_BYTES = 1024 * 1024;
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final FirmwareSettings firmwareSettings;

    public FirmwareStorage(FirmwareSettings firmwareSettings) {
        this.firmwareSettings = firmwareSettings;
    }

    public Path resolveFirmwarePath(String version) {
        return resolveFirmwarePath(version, FirmwareHardwareProfile.ESP32DEV.value());
    }

    public Path resolveFirmwarePath(String version, String hardwareProfile) {
        String safeVersion = requireVersion(version);
        FirmwareHardwareProfile profile = requireHardwareProfile(hardwareProfile);
        Path baseDir = firmwareSettings.getFirmwareDir();
        Path resolved = baseDir.resolve(profile.filename(safeVersion)).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new DomainException("unprocessable", "invalid firmware version");
        }
        return resolved;
    }

    public Path storeFirmware(String version, MultipartFile file) throws IOException {
        return storeFirmware(version, FirmwareHardwareProfile.ESP32DEV.value(), file);
    }

    public Path storeFirmware(String version, String hardwareProfile, MultipartFile file) throws IOException {
        Path firmwareDir = firmwareSettings.getFirmwareDir();
        Files.createDirectories(firmwareDir);
        Path target = resolveFirmwarePath(version, hardwareProfile);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 unavailable", ex);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public List<FirmwareVersionInfo> listFirmwareVersions() {
        Path baseDir = firmwareSettings.getFirmwareDir();
        if (!Files.exists(baseDir)) {
            return List.of();
        }
        List<FirmwareVersionInfo> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.bin")) {
            for (Path path : stream) {
                FirmwareVersionInfo info = toInfo(path);
                if (info != null) {
                    result.add(info);
                }
            }
        } catch (IOException ex) {
            return List.of();
        }
        result.sort((left, right) -> right.mtime().compareTo(left.mtime()));
        return result;
    }

    public FirmwareVersionInfo latestFirmwareVersion() {
        return latestFirmwareVersion(FirmwareHardwareProfile.ESP32DEV.value());
    }

    public FirmwareVersionInfo latestFirmwareVersion(String hardwareProfile) {
        FirmwareHardwareProfile profile = requireHardwareProfile(hardwareProfile);
        List<FirmwareVersionInfo> versions = listFirmwareVersions().stream()
                .filter(info -> profile.value().equals(info.hardwareProfile()))
                .toList();
        return versions.isEmpty() ? null : versions.get(0);
    }

    private FirmwareVersionInfo toInfo(Path path) {
        try {
            String filename = path.getFileName().toString();
            FirmwareHardwareProfile profile = null;
            String version = null;
            for (FirmwareHardwareProfile candidate : FirmwareHardwareProfile.values()) {
                String suffix = "." + candidate.value() + ".bin";
                if (filename.endsWith(suffix)) {
                    profile = candidate;
                    version = filename.substring(0, filename.length() - suffix.length());
                    break;
                }
            }
            if (profile == null || !VERSION_PATTERN.matcher(version).matches()) {
                return null;
            }
            long size = Files.size(path);
            String sha = sha256(path);
            java.time.Instant mtime = Files.getLastModifiedTime(path).toInstant();
            return new FirmwareVersionInfo(version, profile.value(), size, sha, mtime);
        } catch (Exception ex) {
            return null;
        }
    }

    private String requireVersion(String version) {
        String candidate = version != null ? version.trim() : "";
        if (!VERSION_PATTERN.matcher(candidate).matches()) {
            throw new DomainException("unprocessable", "invalid firmware version");
        }
        return candidate;
    }

    private FirmwareHardwareProfile requireHardwareProfile(String hardwareProfile) {
        FirmwareHardwareProfile profile = FirmwareHardwareProfile.fromValue(hardwareProfile);
        if (profile == null) {
            throw new DomainException("unprocessable", "unsupported firmware hardware profile");
        }
        return profile;
    }
}
