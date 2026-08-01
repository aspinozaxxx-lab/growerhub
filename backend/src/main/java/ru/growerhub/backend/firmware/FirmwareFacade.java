﻿package ru.growerhub.backend.firmware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.config.ServerSettings;
import ru.growerhub.backend.common.config.FirmwareSettings;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceFirmwareStatus;
import ru.growerhub.backend.firmware.contract.FirmwareCheckResult;
import ru.growerhub.backend.firmware.contract.FirmwareHardwareProfile;
import ru.growerhub.backend.firmware.contract.FirmwareTriggerResult;
import ru.growerhub.backend.firmware.contract.FirmwareUploadResult;
import ru.growerhub.backend.firmware.contract.FirmwareVersionInfo;
import ru.growerhub.backend.firmware.engine.FirmwareStorage;
import ru.growerhub.backend.firmware.contract.FirmwareUpdateGateway;

@Service
public class FirmwareFacade {
    private final FirmwareStorage firmwareStorage;
    private final FirmwareSettings firmwareSettings;
    private final ServerSettings serverSettings;
    private final FirmwareUpdateGateway updateGateway;
    private final DeviceFacade deviceFacade;

    public FirmwareFacade(
            FirmwareStorage firmwareStorage,
            FirmwareSettings firmwareSettings,
            ServerSettings serverSettings,
            FirmwareUpdateGateway updateGateway,
            DeviceFacade deviceFacade
    ) {
        this.firmwareStorage = firmwareStorage;
        this.firmwareSettings = firmwareSettings;
        this.serverSettings = serverSettings;
        this.updateGateway = updateGateway;
        this.deviceFacade = deviceFacade;
    }

    public FirmwareCheckResult checkFirmwareUpdate(String deviceId) {
        DeviceFirmwareStatus status = getCurrentDeviceStatus(deviceId);
        if (status == null) {
            throw new DomainException("not_found", "Device not found");
        }
        FirmwareHardwareProfile hardwareProfile = FirmwareHardwareProfile.fromValue(status.hardwareProfile());
        FirmwareVersionInfo latest = hardwareProfile != null
                ? firmwareStorage.latestFirmwareVersion(hardwareProfile.value())
                : null;
        String latestVersion = latest != null ? latest.version() : null;
        boolean updateAvailable = latestVersion != null && !latestVersion.equals(status.currentVersion());
        String url = latestVersion != null
                ? buildPublicFirmwareUrl(latestVersion, hardwareProfile)
                : null;
        String error = hardwareProfile == null
                ? "hardware_profile_unknown"
                : status.error();
        return new FirmwareCheckResult(
                updateAvailable,
                status.currentVersion(),
                status.hardwareProfile(),
                latestVersion,
                status.targetVersion(),
                url,
                status.state(),
                error,
                status.requestedAt(),
                status.completedAt(),
                status.online()
        );
    }

    public FirmwareUploadResult uploadFirmware(
            MultipartFile file,
            String version,
            String hardwareProfile
    ) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("internal_error", "empty file");
        }
        Path stored;
        try {
            stored = firmwareStorage.storeFirmware(version, hardwareProfile, file);
        } catch (IOException ex) {
            throw new DomainException("internal_error", "write failed");
        }
        return new FirmwareUploadResult("created", version.trim(), stored.toString());
    }

    public FirmwareTriggerResult triggerLatestUpdate(String deviceId) {
        DeviceFirmwareStatus status = getCurrentDeviceStatus(deviceId);
        if (status == null) {
            throw new DomainException("not_found", "Device not found");
        }
        FirmwareHardwareProfile hardwareProfile = requireHardwareProfile(status);
        FirmwareVersionInfo latest = firmwareStorage.latestFirmwareVersion(hardwareProfile.value());
        if (latest == null) {
            throw new DomainException("not_found", "firmware not found");
        }
        return triggerUpdate(deviceId, latest.version());
    }

    public FirmwareTriggerResult triggerUpdate(String deviceId, String version) {
        DeviceFirmwareStatus status = getCurrentDeviceStatus(deviceId);
        if (status == null) {
            throw new DomainException("not_found", "Device not found");
        }
        if (!status.online()) {
            throw new DomainException("conflict", "Устройство не в сети");
        }
        if (isUpdateInProgress(status)) {
            throw new DomainException("conflict", "Обновление уже выполняется");
        }
        FirmwareHardwareProfile hardwareProfile = requireHardwareProfile(status);
        Path firmwarePath = firmwareStorage.resolveFirmwarePath(version, hardwareProfile.value());
        if (!Files.exists(firmwarePath)) {
            throw new DomainException("not_found", "firmware not found");
        }
        String normalizedVersion = version.trim();
        if (normalizedVersion.equals(status.currentVersion())) {
            throw new DomainException("conflict", "Последняя версия уже установлена");
        }
        String sha256;
        try {
            sha256 = firmwareStorage.sha256(firmwarePath);
        } catch (IOException ex) {
            throw new DomainException("internal_error", "read failed");
        }
        String firmwareUrl = buildPublicFirmwareUrl(normalizedVersion, hardwareProfile);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        deviceFacade.markFirmwareUpdate(
                deviceId,
                normalizedVersion,
                firmwareUrl,
                correlationId,
                requestedAt
        );
        try {
            updateGateway.publishOta(deviceId, firmwareUrl, normalizedVersion, sha256, correlationId);
        } catch (DomainException ex) {
            deviceFacade.markFirmwareUpdateFailed(deviceId, correlationId, ex.getMessage(), LocalDateTime.now(ZoneOffset.UTC));
            throw ex;
        } catch (RuntimeException ex) {
            deviceFacade.markFirmwareUpdateFailed(
                    deviceId,
                    correlationId,
                    "mqtt publish failed",
                    LocalDateTime.now(ZoneOffset.UTC)
            );
            throw new DomainException("unavailable", "mqtt publish failed");
        }
        return new FirmwareTriggerResult("accepted", normalizedVersion, firmwareUrl, sha256, correlationId);
    }

    public List<FirmwareVersionInfo> listFirmwareVersions() {
        return firmwareStorage.listFirmwareVersions();
    }

    public FirmwareVersionInfo toVersionResponse(FirmwareVersionInfo info) {
        return new FirmwareVersionInfo(
                info.version(),
                info.hardwareProfile(),
                info.size(),
                info.sha256(),
                info.mtime()
        );
    }

    private String buildPublicFirmwareUrl(
            String version,
            FirmwareHardwareProfile hardwareProfile
    ) {
        String baseUrl = serverSettings.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new DomainException("internal_error", "public firmware URL is not configured");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/firmware/" + hardwareProfile.filename(version);
    }

    private boolean isUpdateInProgress(DeviceFirmwareStatus status) {
        return status.state() == ru.growerhub.backend.device.contract.DeviceFirmwareUpdateState.QUEUED
                || status.state() == ru.growerhub.backend.device.contract.DeviceFirmwareUpdateState.DOWNLOADING
                || status.state() == ru.growerhub.backend.device.contract.DeviceFirmwareUpdateState.RESTARTING;
    }

    private DeviceFirmwareStatus getCurrentDeviceStatus(String deviceId) {
        DeviceFirmwareStatus status = deviceFacade.getFirmwareStatus(deviceId);
        if (status == null || !isUpdateInProgress(status) || status.requestedAt() == null) {
            return status;
        }
        LocalDateTime timeoutAt = status.requestedAt().plusSeconds(firmwareSettings.getUpdateTimeoutSeconds());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (timeoutAt.isAfter(now)) {
            return status;
        }
        deviceFacade.markFirmwareUpdateFailed(
                deviceId,
                status.correlationId(),
                "device_update_timeout",
                now
        );
        return deviceFacade.getFirmwareStatus(deviceId);
    }

    private FirmwareHardwareProfile requireHardwareProfile(DeviceFirmwareStatus status) {
        FirmwareHardwareProfile profile = FirmwareHardwareProfile.fromValue(status.hardwareProfile());
        if (profile == null) {
            throw new DomainException("conflict", "Профиль устройства не определён");
        }
        return profile;
    }
}
