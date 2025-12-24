package ru.growerhub.backend.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.api.dto.FirmwareDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.firmware.FirmwareSettings;
import ru.growerhub.backend.firmware.ServerSettings;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdOta;

@RestController
@Validated
public class FirmwareController {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DeviceRepository deviceRepository;
    private final FirmwareSettings firmwareSettings;
    private final ServerSettings serverSettings;
    private final ObjectProvider<MqttPublisher> publisherProvider;

    public FirmwareController(
            DeviceRepository deviceRepository,
            FirmwareSettings firmwareSettings,
            ServerSettings serverSettings,
            ObjectProvider<MqttPublisher> publisherProvider
    ) {
        this.deviceRepository = deviceRepository;
        this.firmwareSettings = firmwareSettings;
        this.serverSettings = serverSettings;
        this.publisherProvider = publisherProvider;
    }

    @GetMapping("/api/device/{device_id}/firmware")
    public FirmwareDtos.FirmwareCheckResponse checkFirmware(
            @PathVariable("device_id") String deviceId
    ) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null || !Boolean.TRUE.equals(device.getUpdateAvailable())) {
            return new FirmwareDtos.FirmwareCheckResponse(false, null, null);
        }
        String version = device.getLatestVersion();
        String url = "http://192.168.0.11/firmware/" + version + ".bin";
        return new FirmwareDtos.FirmwareCheckResponse(true, version, url);
    }

    @PostMapping(
            value = "/api/upload-firmware",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public FirmwareDtos.UploadFirmwareResponse uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version
    ) {
        Path firmwareDir = firmwareSettings.getFirmwareDir();
        Path filePath = firmwareDir.resolve(version + ".bin");
        try {
            Files.createDirectories(firmwareDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (AccessDeniedException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "permission denied");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "write failed");
        }

        long size;
        try {
            size = Files.size(filePath);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "write failed");
        }
        if (size <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "empty file");
        }

        return new FirmwareDtos.UploadFirmwareResponse("created", version, filePath.toString());
    }

    @PostMapping("/api/device/{device_id}/trigger-update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public FirmwareDtos.TriggerFirmwareUpdateResponse triggerUpdate(
            @PathVariable("device_id") String deviceId,
            @RequestBody FirmwareDtos.TriggerFirmwareUpdateRequest request
    ) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Device not found");
        }

        String version = resolveVersion(request);
        Path firmwarePath = firmwareSettings.getFirmwareDir().resolve(version + ".bin");
        if (!Files.exists(firmwarePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "firmware not found");
        }

        String sha256 = sha256File(firmwarePath);
        String url = buildFirmwareUrl(version);
        CmdOta cmd = new CmdOta("ota", url, version, sha256);
        try {
            getPublisher().publishCmd(deviceId, cmd);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mqtt publish failed");
        }

        device.setUpdateAvailable(true);
        device.setLatestVersion(version);
        device.setFirmwareUrl(url);
        deviceRepository.save(device);

        return new FirmwareDtos.TriggerFirmwareUpdateResponse("accepted", version, url, sha256);
    }

    @GetMapping("/api/firmware/versions")
    public ResponseEntity<List<FirmwareDtos.FirmwareVersionResponse>> listFirmwareVersions() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        Path baseDir = firmwareSettings.getFirmwareDir();
        if (!Files.exists(baseDir)) {
            return new ResponseEntity<>(List.of(), headers, HttpStatus.OK);
        }

        List<FirmwareVersionItem> items = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.bin")) {
            for (Path binPath : stream) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(binPath, BasicFileAttributes.class);
                } catch (IOException ex) {
                    continue;
                }
                String version = stripExtension(binPath.getFileName().toString());
                long size = attrs.size();
                String sha256 = sha256File(binPath);
                Instant mtime = attrs.lastModifiedTime().toInstant();
                String mtimeIso = formatIsoUtc(mtime);
                items.add(new FirmwareVersionItem(version, size, sha256, mtime, mtimeIso));
            }
        } catch (IOException ex) {
            return new ResponseEntity<>(List.of(), headers, HttpStatus.OK);
        }

        items.sort(Comparator.comparing(FirmwareVersionItem::mtime).reversed());
        List<FirmwareDtos.FirmwareVersionResponse> payload = new ArrayList<>();
        for (FirmwareVersionItem item : items) {
            payload.add(new FirmwareDtos.FirmwareVersionResponse(
                    item.version(),
                    item.size(),
                    item.sha256(),
                    item.mtimeIso()
            ));
        }

        return new ResponseEntity<>(payload, headers, HttpStatus.OK);
    }

    private MqttPublisher getPublisher() {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT publisher unavailable");
        }
        return publisher;
    }

    private String buildFirmwareUrl(String version) {
        String prefix = serverSettings.getPublicBaseUrl();
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/firmware/" + version + ".bin";
    }

    private String resolveVersion(FirmwareDtos.TriggerFirmwareUpdateRequest request) {
        String version = normalize(request != null ? request.version() : null);
        if (version == null) {
            version = normalize(request != null ? request.firmwareVersion() : null);
        }
        if (version == null) {
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "__root__"),
                    "version required",
                    "value_error"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
        return version;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sha256File(Path path) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String formatIsoUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).format(ISO_UTC).replace("+00:00", "Z");
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private record FirmwareVersionItem(
            String version,
            long size,
            String sha256,
            Instant mtime,
            String mtimeIso
    ) {
    }
}
