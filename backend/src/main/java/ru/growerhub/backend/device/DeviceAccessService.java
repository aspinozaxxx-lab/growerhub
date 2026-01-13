package ru.growerhub.backend.device;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.contract.DeviceFirmwareStatus;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.device.engine.DeviceQueryService;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;

@Service
public class DeviceAccessService {
    private final DeviceRepository deviceRepository;
    private final DeviceQueryService deviceQueryService;
    private final DeviceShadowStore shadowStore;

    public DeviceAccessService(DeviceRepository deviceRepository, DeviceQueryService deviceQueryService, DeviceShadowStore shadowStore) {
        this.deviceRepository = deviceRepository;
        this.deviceQueryService = deviceQueryService;
        this.shadowStore = shadowStore;
    }

    public Integer findDeviceId(String deviceId) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        return device != null ? device.getId() : null;
    }

    @Transactional(readOnly = true)
    public DeviceSummary getDeviceSummary(Integer deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        return device != null ? deviceQueryService.buildDeviceSummary(device) : null;
    }

    @Transactional(readOnly = true)
    public DeviceFirmwareStatus getFirmwareStatus(String deviceId) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return null;
        }
        return new DeviceFirmwareStatus(device.getUpdateAvailable(), device.getLatestVersion(), device.getFirmwareUrl());
    }

    @Transactional
    public void markFirmwareUpdate(String deviceId, String version, String firmwareUrl) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Device not found");
        }
        device.setUpdateAvailable(true);
        device.setLatestVersion(version);
        device.setFirmwareUrl(firmwareUrl);
        deviceRepository.save(device);
    }

    @Transactional
    public void updateManualWateringState(
            String deviceId,
            DeviceShadowState.ManualWateringState manualState,
            LocalDateTime updatedAt
    ) {
        DeviceShadowState state = new DeviceShadowState(
                manualState,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        shadowStore.updateFromStateAndPersist(deviceId, state, updatedAt);
    }

    public Map<String, Object> getManualWateringView(String deviceId) {
        return shadowStore.getManualWateringView(deviceId);
    }
}
