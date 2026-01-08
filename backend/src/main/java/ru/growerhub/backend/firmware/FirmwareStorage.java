package ru.growerhub.backend.firmware;

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
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FirmwareStorage {
    private static final int READ_BUFFER_BYTES = 1024 * 1024;

    private final FirmwareSettings firmwareSettings;

    public FirmwareStorage(FirmwareSettings firmwareSettings) {
        this.firmwareSettings = firmwareSettings;
    }

    public Path resolveFirmwarePath(String version) {
        return firmwareSettings.getFirmwareDir().resolve(version + ".bin");
    }

    public Path storeFirmware(String version, MultipartFile file) throws IOException {
        Path firmwareDir = firmwareSettings.getFirmwareDir();
        Files.createDirectories(firmwareDir);
        Path target = resolveFirmwarePath(version);
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

    private FirmwareVersionInfo toInfo(Path path) {
        try {
            String filename = path.getFileName().toString();
            String version = filename.endsWith(".bin")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;
            long size = Files.size(path);
            String sha = sha256(path);
            java.time.Instant mtime = Files.getLastModifiedTime(path).toInstant();
            return new FirmwareVersionInfo(version, size, sha, mtime);
        } catch (Exception ex) {
            return null;
        }
    }
}