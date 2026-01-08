package ru.growerhub.backend.device;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.api.dto.DeviceDtos;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.pump.PumpQueryService;
import ru.growerhub.backend.pump.PumpView;
import ru.growerhub.backend.sensor.SensorQueryService;
import ru.growerhub.backend.sensor.SensorView;
import ru.growerhub.backend.user.UserEntity;

@Service
public class DeviceQueryService {
    private static final String DEFAULT_FIRMWARE_VERSION = "old";

    private final DeviceRepository deviceRepository;
    private final DeviceShadowStore shadowStore;
    private final SensorQueryService sensorQueryService;
    private final PumpQueryService pumpQueryService;

    public DeviceQueryService(
            DeviceRepository deviceRepository,
            DeviceShadowStore shadowStore,
            SensorQueryService sensorQueryService,
            PumpQueryService pumpQueryService
    ) {
        this.deviceRepository = deviceRepository;
        this.shadowStore = shadowStore;
        this.sensorQueryService = sensorQueryService;
        this.pumpQueryService = pumpQueryService;
    }

    public List<DeviceDtos.DeviceResponse> listMyDevices(Integer userId) {
        List<DeviceEntity> devices = deviceRepository.findAllByUser_Id(userId);
        List<DeviceDtos.DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(buildDeviceResponse(device));
        }
        return responses;
    }

    public List<DeviceDtos.DeviceResponse> listDevices() {
        List<DeviceEntity> devices = deviceRepository.findAll();
        List<DeviceDtos.DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(buildDeviceResponse(device));
        }
        return responses;
    }

    public List<DeviceDtos.AdminDeviceResponse> listAdminDevices() {
        List<DeviceEntity> devices = deviceRepository.findAll();
        List<DeviceDtos.AdminDeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            DeviceDtos.DeviceResponse base = buildDeviceResponse(device);
            UserEntity owner = device.getUser();
            DeviceDtos.DeviceOwnerInfoResponse ownerPayload = owner != null
                    ? new DeviceDtos.DeviceOwnerInfoResponse(owner.getId(), owner.getEmail(), owner.getUsername())
                    : null;
            responses.add(new DeviceDtos.AdminDeviceResponse(
                    base.id(),
                    base.deviceId(),
                    base.name(),
                    base.isOnline(),
                    base.lastSeen(),
                    base.targetMoisture(),
                    base.wateringDuration(),
                    base.wateringTimeout(),
                    base.lightOnHour(),
                    base.lightOffHour(),
                    base.lightDuration(),
                    base.currentVersion(),
                    base.updateAvailable(),
                    base.firmwareVersion(),
                    base.userId(),
                    base.sensors(),
                    base.pumps(),
                    ownerPayload
            ));
        }
        return responses;
    }

    public DeviceDtos.DeviceResponse buildDeviceResponse(DeviceEntity device) {
        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(device.getDeviceId());
        DeviceState state = snapshot != null ? snapshot.state() : null;
        LocalDateTime lastSeen = snapshot != null ? snapshot.updatedAt() : device.getLastSeen();
        boolean isOnline = snapshot != null ? snapshot.isOnline() : resolveOnlineFromDevice(device);
        String firmwareVersion = resolveFirmwareVersion(state);

        List<DeviceDtos.SensorResponse> sensors = mapSensors(sensorQueryService.listByDevice(device));
        List<DeviceDtos.PumpResponse> pumps = mapPumps(pumpQueryService.listByDevice(device, state));

        UserEntity owner = device.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new DeviceDtos.DeviceResponse(
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
                ownerId,
                sensors,
                pumps
        );
    }

    private boolean resolveOnlineFromDevice(DeviceEntity device) {
        if (device == null || device.getLastSeen() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return Duration.between(device.getLastSeen(), now).compareTo(Duration.ofMinutes(3)) <= 0;
    }

    private String resolveFirmwareVersion(DeviceState state) {
        if (state == null || state.fwVer() == null || state.fwVer().isBlank()) {
            return DEFAULT_FIRMWARE_VERSION;
        }
        return state.fwVer();
    }

    private List<DeviceDtos.SensorResponse> mapSensors(List<SensorView> views) {
        List<DeviceDtos.SensorResponse> result = new ArrayList<>();
        for (SensorView view : views) {
            List<DeviceDtos.BoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.BoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.growthStage(),
                            plant.ageDays()
                    ));
                }
            }
            result.add(new DeviceDtos.SensorResponse(
                    view.id(),
                    view.type() != null ? view.type().name() : null,
                    view.channel(),
                    view.label(),
                    view.detected(),
                    view.lastValue(),
                    view.lastTs(),
                    plants
            ));
        }
        return result;
    }

    private List<DeviceDtos.PumpResponse> mapPumps(List<PumpView> views) {
        List<DeviceDtos.PumpResponse> result = new ArrayList<>();
        for (PumpView view : views) {
            List<DeviceDtos.PumpBoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.PumpBoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.ageDays(),
                            plant.rateMlPerHour()
                    ));
                }
            }
            result.add(new DeviceDtos.PumpResponse(
                    view.id(),
                    view.channel(),
                    view.label(),
                    view.isRunning(),
                    plants
            ));
        }
        return result;
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
