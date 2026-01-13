package ru.growerhub.backend.firmware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.device.contract.DeviceFirmwareStatus;
import ru.growerhub.backend.firmware.internal.FirmwareStorage;

@Service
public class FirmwareFacade {
    private static final String DEVICE_FIRMWARE_BASE_URL = "http://192.168.0.11";

    private final FirmwareStorage firmwareStorage;
    private final ServerSettings serverSettings;
    private final FirmwareUpdateGateway updateGateway;
    private final DeviceAccessService deviceAccessService;

    public FirmwareFacade(
            FirmwareStorage firmwareStorage,
            ServerSettings serverSettings,
            FirmwareUpdateGateway updateGateway,
            DeviceAccessService deviceAccessService
    ) {
        this.firmwareStorage = firmwareStorage;
        this.serverSettings = serverSettings;
        this.updateGateway = updateGateway;
        this.deviceAccessService = deviceAccessService;
    }

    public FirmwareCheckResult checkFirmwareUpdate(String deviceId) {
        DeviceFirmwareStatus status = deviceAccessService.getFirmwareStatus(deviceId);
        if (status == null || !Boolean.TRUE.equals(status.updateAvailable())) {
            return new FirmwareCheckResult(false, null, null);
        }
        String latestVersion = status.latestVersion();
        if (latestVersion == null || latestVersion.isBlank()) {
            return new FirmwareCheckResult(false, null, null);
        }
        String url = DEVICE_FIRMWARE_BASE_URL + "/firmware/" + latestVersion + ".bin";
        return new FirmwareCheckResult(true, latestVersion, url);
    }

    public FirmwareUploadResult uploadFirmware(MultipartFile file, String version) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("internal_error", "empty file");
        }
        Path stored;
        try {
            stored = firmwareStorage.storeFirmware(version, file);
        } catch (IOException ex) {
            throw new DomainException("internal_error", "write failed");
        }
        return new FirmwareUploadResult("created", version, stored.toString());
    }

    @Transactional
    public FirmwareTriggerResult triggerUpdate(String deviceId, String version) {
        if (deviceAccessService.findDeviceId(deviceId) == null) {
            throw new DomainException("not_found", "Device not found");
        }
        Path firmwarePath = firmwareStorage.resolveFirmwarePath(version);
        if (!Files.exists(firmwarePath)) {
            throw new DomainException("not_found", "firmware not found");
        }
        String sha256;
        try {
            sha256 = firmwareStorage.sha256(firmwarePath);
        } catch (IOException ex) {
            throw new DomainException("internal_error", "read failed");
        }
        String firmwareUrl = buildPublicFirmwareUrl(version);
        try {
            updateGateway.publishOta(deviceId, firmwareUrl, version, sha256);
        } catch (DomainException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DomainException("unavailable", "mqtt publish failed");
        }
        deviceAccessService.markFirmwareUpdate(deviceId, version, firmwareUrl);
        return new FirmwareTriggerResult("accepted", version, firmwareUrl, sha256);
    }

    public List<FirmwareVersionInfo> listFirmwareVersions() {
        return firmwareStorage.listFirmwareVersions();
    }

    public FirmwareVersionInfo toVersionResponse(FirmwareVersionInfo info) {
        String mtime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(info.mtime().atOffset(ZoneOffset.UTC));
        return new FirmwareVersionInfo(info.version(), info.size(), info.sha256(), info.mtime());
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
