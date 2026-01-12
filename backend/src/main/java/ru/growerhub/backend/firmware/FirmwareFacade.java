package ru.growerhub.backend.firmware;

import jakarta.persistence.EntityManager;
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
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.firmware.internal.FirmwareStorage;

@Service
public class FirmwareFacade {
    private static final String DEVICE_FIRMWARE_BASE_URL = "http://192.168.0.11";

    private final FirmwareStorage firmwareStorage;
    private final ServerSettings serverSettings;
    private final FirmwareUpdateGateway updateGateway;
    private final EntityManager entityManager;

    public FirmwareFacade(
            FirmwareStorage firmwareStorage,
            ServerSettings serverSettings,
            FirmwareUpdateGateway updateGateway,
            EntityManager entityManager
    ) {
        this.firmwareStorage = firmwareStorage;
        this.serverSettings = serverSettings;
        this.updateGateway = updateGateway;
        this.entityManager = entityManager;
    }

    public FirmwareCheckResult checkFirmwareUpdate(String deviceId) {
        DeviceEntity device = findDeviceByDeviceId(deviceId);
        if (device == null || !Boolean.TRUE.equals(device.getUpdateAvailable())) {
            return new FirmwareCheckResult(false, null, null);
        }
        String latestVersion = device.getLatestVersion();
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
        DeviceEntity device = findDeviceByDeviceId(deviceId);
        if (device == null) {
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
        device.setUpdateAvailable(true);
        device.setLatestVersion(version);
        device.setFirmwareUrl(firmwareUrl);
        entityManager.merge(device);
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

    private DeviceEntity findDeviceByDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        List<DeviceEntity> result = entityManager
                .createQuery("select d from DeviceEntity d where d.deviceId = :deviceId", DeviceEntity.class)
                .setParameter("deviceId", deviceId)
                .setMaxResults(1)
                .getResultList();
        return result.isEmpty() ? null : result.get(0);
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
