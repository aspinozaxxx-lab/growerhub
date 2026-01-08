package ru.growerhub.backend.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.api.dto.FirmwareDtos;
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.device.DeviceRepository;
import ru.growerhub.backend.firmware.FirmwareStorage;
import ru.growerhub.backend.firmware.FirmwareVersionInfo;
import ru.growerhub.backend.firmware.ServerSettings;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdOta;

@RestController
@Validated
public class FirmwareController {
    private static final String DEVICE_FIRMWARE_BASE_URL = "http://192.168.0.11";

    private final FirmwareStorage firmwareStorage;
    private final ServerSettings serverSettings;
    private final DeviceRepository deviceRepository;
    private final ObjectProvider<MqttPublisher> publisherProvider;

    public FirmwareController(
            FirmwareStorage firmwareStorage,
            ServerSettings serverSettings,
            DeviceRepository deviceRepository,
            ObjectProvider<MqttPublisher> publisherProvider
    ) {
        this.firmwareStorage = firmwareStorage;
        this.serverSettings = serverSettings;
        this.deviceRepository = deviceRepository;
        this.publisherProvider = publisherProvider;
    }

    @GetMapping("/api/device/{device_id}/firmware")
    public FirmwareDtos.FirmwareCheckResponse checkFirmwareUpdate(
            @PathVariable("device_id") String deviceId
    ) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null || !Boolean.TRUE.equals(device.getUpdateAvailable())) {
            return new FirmwareDtos.FirmwareCheckResponse(false, null, null);
        }
        String latestVersion = device.getLatestVersion();
        if (latestVersion == null || latestVersion.isBlank()) {
            return new FirmwareDtos.FirmwareCheckResponse(false, null, null);
        }
        String url = DEVICE_FIRMWARE_BASE_URL + "/firmware/" + latestVersion + ".bin";
        return new FirmwareDtos.FirmwareCheckResponse(true, latestVersion, url);
    }

    @PostMapping("/api/upload-firmware")
    public ResponseEntity<FirmwareDtos.UploadFirmwareResponse> uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "empty file");
        }
        Path stored;
        try {
            stored = firmwareStorage.storeFirmware(version, file);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "write failed");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FirmwareDtos.UploadFirmwareResponse("created", version, stored.toString()));
    }

    @PostMapping("/api/device/{device_id}/trigger-update")
    public ResponseEntity<FirmwareDtos.TriggerFirmwareUpdateResponse> triggerUpdate(
            @PathVariable("device_id") String deviceId,
            @RequestBody FirmwareDtos.TriggerFirmwareUpdateRequest request
    ) {
        String version = resolveVersion(request);
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Device not found");
        }
        Path firmwarePath = firmwareStorage.resolveFirmwarePath(version);
        if (!Files.exists(firmwarePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "firmware not found");
        }
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT publisher unavailable");
        }
        String sha256;
        try {
            sha256 = firmwareStorage.sha256(firmwarePath);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "read failed");
        }
        String firmwareUrl = buildPublicFirmwareUrl(version);
        CmdOta cmd = new CmdOta("ota", firmwareUrl, version, sha256);
        try {
            publisher.publishCmd(deviceId, cmd);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mqtt publish failed");
        }
        device.setUpdateAvailable(true);
        device.setLatestVersion(version);
        device.setFirmwareUrl(firmwareUrl);
        deviceRepository.save(device);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FirmwareDtos.TriggerFirmwareUpdateResponse("accepted", version, firmwareUrl, sha256));
    }

    @GetMapping("/api/firmware/versions")
    public ResponseEntity<List<FirmwareDtos.FirmwareVersionResponse>> listFirmwareVersions() {
        List<FirmwareVersionInfo> versions = firmwareStorage.listFirmwareVersions();
        List<FirmwareDtos.FirmwareVersionResponse> response = versions.stream()
                .map(this::toVersionResponse)
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private FirmwareDtos.FirmwareVersionResponse toVersionResponse(FirmwareVersionInfo info) {
        String mtime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(info.mtime().atOffset(ZoneOffset.UTC));
        return new FirmwareDtos.FirmwareVersionResponse(
                info.version(),
                info.size(),
                info.sha256(),
                mtime
        );
    }

    private String resolveVersion(FirmwareDtos.TriggerFirmwareUpdateRequest request) {
        String candidate = request != null ? request.version() : null;
        if (candidate == null || candidate.isBlank()) {
            candidate = request != null ? request.firmwareVersion() : null;
        }
        if (candidate == null || candidate.isBlank()) {
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "__root__"),
                    "version required",
                    "value_error"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
        return candidate;
    }

    private String buildPublicFirmwareUrl(String version) {
        String baseUrl = serverSettings.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEVICE_FIRMWARE_BASE_URL;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/firmware/" + version + ".bin";
    }
}