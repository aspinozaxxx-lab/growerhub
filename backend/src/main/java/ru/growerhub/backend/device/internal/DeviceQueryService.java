package ru.growerhub.backend.device.internal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.user.UserEntity;

@Service
public class DeviceQueryService {
    private static final String DEFAULT_FIRMWARE_VERSION = "old";

    private final DeviceRepository deviceRepository;
    private final DeviceShadowStore shadowStore;

    public DeviceQueryService(
            DeviceRepository deviceRepository,
            DeviceShadowStore shadowStore
    ) {
        this.deviceRepository = deviceRepository;
        this.shadowStore = shadowStore;
    }

    public List<DeviceSummary> listMyDevices(Integer userId) {
        List<DeviceEntity> devices = deviceRepository.findAllByUser_Id(userId);
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

        UserEntity owner = device.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new DeviceSummary(
                device.getId(),
                device.getDeviceId(),
                defaultString(device.getName(), DeviceDefaults.defaultName(device.getDeviceId())),
                isOnline,
                lastSeen,
                defaultDouble(device.getTargetMoisture(), DeviceDefaults.TARGET_MOISTURE),
                defaultInteger(device.getWateringDuration(), DeviceDefaults.WATERING_DURATION),
                defaultInteger(device.getWateringTimeout(), DeviceDefaults.WATERING_TIMEOUT),
                defaultInteger(device.getLightOnHour(), DeviceDefaults.LIGHT_ON_HOUR),
                defaultInteger(device.getLightOffHour(), DeviceDefaults.LIGHT_OFF_HOUR),
                defaultInteger(device.getLightDuration(), DeviceDefaults.LIGHT_DURATION),
                defaultString(device.getCurrentVersion(), DeviceDefaults.CURRENT_VERSION),
                defaultBoolean(device.getUpdateAvailable(), DeviceDefaults.UPDATE_AVAILABLE),
                firmwareVersion,
                ownerId
        );
    }

    private boolean resolveOnlineFromDevice(DeviceEntity device) {
        if (device == null || device.getLastSeen() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Duration.between(device.getLastSeen(), now).compareTo(Duration.ofMinutes(3)) <= 0;
    }

    private String resolveFirmwareVersion(DeviceShadowState state) {
        if (state == null || state.fwVer() == null || state.fwVer().isBlank()) {
            return DEFAULT_FIRMWARE_VERSION;
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
