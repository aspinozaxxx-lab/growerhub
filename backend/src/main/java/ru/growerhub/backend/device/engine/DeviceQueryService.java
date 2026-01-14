package ru.growerhub.backend.device.engine;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.DeviceSettings;
import ru.growerhub.backend.common.config.device.DeviceDefaultsSettings;
import ru.growerhub.backend.common.config.device.DeviceFirmwareSettings;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;

@Service
public class DeviceQueryService {
    private final DeviceRepository deviceRepository;
    private final DeviceShadowStore shadowStore;
    private final DeviceDefaultsSettings defaultsSettings;
    private final DeviceFirmwareSettings firmwareSettings;
    private final DeviceSettings deviceSettings;

    public DeviceQueryService(
            DeviceRepository deviceRepository,
            DeviceShadowStore shadowStore,
            DeviceDefaultsSettings defaultsSettings,
            DeviceFirmwareSettings firmwareSettings,
            DeviceSettings deviceSettings
    ) {
        this.deviceRepository = deviceRepository;
        this.shadowStore = shadowStore;
        this.defaultsSettings = defaultsSettings;
        this.firmwareSettings = firmwareSettings;
        this.deviceSettings = deviceSettings;
    }

    public List<DeviceSummary> listMyDevices(Integer userId) {
        List<DeviceEntity> devices = deviceRepository.findAllByUserId(userId);
        List<DeviceSummary> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(buildDeviceSummary(device));
        }
        return responses;
    }

    public List<DeviceSummary> listDevices() {
        List<DeviceEntity> devices = deviceRepository.findAll();
        List<DeviceSummary> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(buildDeviceSummary(device));
        }
        return responses;
    }

    public List<DeviceSummary> listAdminDevices() {
        return listDevices();
    }

    public DeviceSummary buildDeviceSummary(DeviceEntity device) {
        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(device.getDeviceId());
        DeviceShadowState state = snapshot != null ? snapshot.state() : null;
        LocalDateTime lastSeen = snapshot != null ? snapshot.updatedAt() : device.getLastSeen();
        boolean isOnline = snapshot != null ? snapshot.isOnline() : resolveOnlineFromDevice(device);
        String firmwareVersion = resolveFirmwareVersion(state);

        return new DeviceSummary(
                device.getId(),
                device.getDeviceId(),
                defaultString(device.getName(), DeviceDefaults.defaultName(device.getDeviceId())),
                isOnline,
                lastSeen,
                defaultDouble(device.getTargetMoisture(), defaultsSettings.getTargetMoisture()),
                defaultInteger(device.getWateringDuration(), defaultsSettings.getWateringDurationSeconds()),
                defaultInteger(device.getWateringTimeout(), defaultsSettings.getWateringTimeoutSeconds()),
                defaultInteger(device.getLightOnHour(), defaultsSettings.getLightOnHour()),
                defaultInteger(device.getLightOffHour(), defaultsSettings.getLightOffHour()),
                defaultInteger(device.getLightDuration(), defaultsSettings.getLightDurationHours()),
                defaultString(device.getCurrentVersion(), defaultsSettings.getCurrentVersion()),
                defaultBoolean(device.getUpdateAvailable(), defaultsSettings.isUpdateAvailable()),
                firmwareVersion,
                device.getUserId()
        );
    }

    private boolean resolveOnlineFromDevice(DeviceEntity device) {
        if (device == null || device.getLastSeen() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int threshold = deviceSettings.getOnlineThresholdS();
        return Duration.between(device.getLastSeen(), now).getSeconds() <= threshold;
    }

    private String resolveFirmwareVersion(DeviceShadowState state) {
        if (state == null || state.fwVer() == null || state.fwVer().isBlank()) {
            return firmwareSettings.getDefaultVersion();
        }
        return state.fwVer();
    }

    private Double defaultDouble(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private Integer defaultInteger(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }
}


