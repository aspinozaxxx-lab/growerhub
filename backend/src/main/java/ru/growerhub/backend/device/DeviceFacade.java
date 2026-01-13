package ru.growerhub.backend.device;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.contract.DeviceAggregate;
import ru.growerhub.backend.device.contract.DeviceSettingsData;
import ru.growerhub.backend.device.contract.DeviceSettingsUpdate;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.device.engine.DeviceAckService;
import ru.growerhub.backend.device.engine.DeviceIngestionService;
import ru.growerhub.backend.device.engine.DeviceQueryService;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.SensorMeasurement;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.user.UserEntity;

@Service
public class DeviceFacade {
    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final DeviceIngestionService deviceIngestionService;
    private final DeviceQueryService deviceQueryService;
    private final DeviceShadowStore shadowStore;
    private final DeviceAckService ackService;
    private final SensorFacade sensorFacade;
    private final PlantFacade plantFacade;
    private final PumpFacade pumpFacade;
    private final EntityManager entityManager;

    public DeviceFacade(
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            DeviceIngestionService deviceIngestionService,
            DeviceQueryService deviceQueryService,
            DeviceShadowStore shadowStore,
            DeviceAckService ackService,
            SensorFacade sensorFacade,
            PlantFacade plantFacade,
            @Lazy PumpFacade pumpFacade,
            EntityManager entityManager
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.deviceIngestionService = deviceIngestionService;
        this.deviceQueryService = deviceQueryService;
        this.shadowStore = shadowStore;
        this.ackService = ackService;
        this.sensorFacade = sensorFacade;
        this.plantFacade = plantFacade;
        this.pumpFacade = pumpFacade;
        this.entityManager = entityManager;
    }

    @Transactional
    public void handleState(String deviceId, DeviceShadowState state, LocalDateTime now) {
        List<SensorMeasurement> measurements = deviceIngestionService.handleState(deviceId, state, now);
        List<SensorReadingSummary> summaries = sensorFacade.recordMeasurements(deviceId, measurements, now);
        plantFacade.recordFromSensorBindings(summaries);
    }

    @Transactional
    public void handleAck(
            String deviceId,
            String correlationId,
            String result,
            String status,
            Map<String, Object> payloadMap,
            LocalDateTime receivedAt,
            LocalDateTime expiresAt
    ) {
        ackService.upsertAck(deviceId, correlationId, result, status, payloadMap, receivedAt, expiresAt);
        touchLastSeen(deviceId, receivedAt);
    }

    @Transactional
    public void touchLastSeen(String deviceId, LocalDateTime now) {
        DeviceStateLastEntity record = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        if (record == null) {
            record = DeviceStateLastEntity.create();
            record.setDeviceId(deviceId);
            record.setStateJson("{}");
        }
        record.setUpdatedAt(now);
        deviceStateLastRepository.save(record);

        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device != null) {
            device.setLastSeen(now);
            deviceRepository.save(device);
        }
    }

    @Transactional(readOnly = true)
    public DeviceShadowState getShadowState(String deviceId) {
        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(deviceId);
        return snapshot != null ? snapshot.state() : null;
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

    @Transactional(readOnly = true)
    public Map<String, Object> getManualWateringView(String deviceId) {
        return shadowStore.getManualWateringView(deviceId);
    }

    @Transactional
    public DeviceSettingsData getSettings(String deviceId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = deviceIngestionService.ensureDeviceExists(deviceId, now);
        if (device == null) {
            device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName(deviceIngestionService.defaultName(deviceId));
            device.setLastSeen(now);
            deviceIngestionService.applyDefaults(device, deviceId);
            deviceRepository.save(device);
        }
        return new DeviceSettingsData(
                defaultDouble(device.getTargetMoisture(), deviceIngestionService.defaultTargetMoisture()),
                defaultInteger(device.getWateringDuration(), deviceIngestionService.defaultWateringDuration()),
                defaultInteger(device.getWateringTimeout(), deviceIngestionService.defaultWateringTimeout()),
                defaultInteger(device.getLightOnHour(), deviceIngestionService.defaultLightOnHour()),
                defaultInteger(device.getLightOffHour(), deviceIngestionService.defaultLightOffHour()),
                defaultInteger(device.getLightDuration(), deviceIngestionService.defaultLightDuration()),
                defaultBoolean(device.getUpdateAvailable(), deviceIngestionService.defaultUpdateAvailable())
        );
    }

    @Transactional
    public void updateSettings(String deviceId, DeviceSettingsUpdate update) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Device not found");
        }
        device.setTargetMoisture(update.targetMoisture());
        device.setWateringDuration(update.wateringDuration());
        device.setWateringTimeout(update.wateringTimeout());
        device.setLightOnHour(update.lightOnHour());
        device.setLightOffHour(update.lightOffHour());
        device.setLightDuration(update.lightDuration());
        deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceSummary> listDevices() {
        return deviceQueryService.listDevices();
    }

    @Transactional(readOnly = true)
    public List<DeviceSummary> listMyDevices(Integer userId) {
        return deviceQueryService.listMyDevices(userId);
    }

    @Transactional(readOnly = true)
    public List<DeviceSummary> listAdminDevices() {
        return deviceQueryService.listAdminDevices();
    }

    @Transactional
    public DeviceSummary assignToUser(Integer deviceId, Integer userId) {
        DeviceEntity device = requireDevice(deviceId);
        UserEntity owner = device.getUser();
        if (owner == null) {
            device.setUser(getUserReference(userId));
        } else if (!owner.getId().equals(userId)) {
            throw new DomainException("bad_request", "ustrojstvo uzhe privyazano k drugomu polzovatelju");
        }
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceSummary unassignForUser(Integer deviceId, Integer userId, boolean isAdmin) {
        DeviceEntity device = requireDevice(deviceId);
        UserEntity owner = device.getUser();
        if (!isAdmin) {
            if (owner == null || !owner.getId().equals(userId)) {
                throw new DomainException("forbidden", "nedostatochno prav dlya otvyazki etogo ustrojstva");
            }
        }
        device.setUser(null);
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceAggregate assignToUserAggregate(Integer deviceId, Integer userId) {
        DeviceSummary summary = assignToUser(deviceId, userId);
        return buildAggregate(summary);
    }

    @Transactional
    public DeviceAggregate unassignForUserAggregate(Integer deviceId, Integer userId, boolean isAdmin) {
        DeviceSummary summary = unassignForUser(deviceId, userId, isAdmin);
        return buildAggregate(summary);
    }

    @Transactional
    public DeviceSummary adminAssign(Integer deviceId, Integer userId) {
        DeviceEntity device = requireDevice(deviceId);
        device.setUser(getUserReference(userId));
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceSummary adminUnassign(Integer deviceId) {
        DeviceEntity device = requireDevice(deviceId);
        device.setUser(null);
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public void deleteDevice(String deviceId) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Device not found");
        }
        Integer id = device.getId();
        if (id != null) {
            sensorFacade.deleteByDeviceId(id);
            pumpFacade.deleteByDeviceId(id);
        }
        deviceStateLastRepository.deleteByDeviceId(deviceId);
        deviceRepository.delete(device);
    }

    @Transactional
    public void unassignDevicesForUser(Integer userId) {
        if (userId == null) {
            return;
        }
        List<DeviceEntity> devices = deviceRepository.findAllByUser_Id(userId);
        for (DeviceEntity device : devices) {
            device.setUser(null);
        }
        deviceRepository.saveAll(devices);
    }

    private DeviceEntity requireDevice(Integer deviceId) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "ustrojstvo ne najdeno");
        }
        return device;
    }

    private UserEntity getUserReference(Integer userId) {
        if (userId == null) {
            return null;
        }
        return entityManager.getReference(UserEntity.class, userId);
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

    private DeviceAggregate buildAggregate(DeviceSummary summary) {
        DeviceShadowState state = getShadowState(summary.deviceId());
        List<SensorView> sensors = sensorFacade.listByDeviceId(summary.id());
        List<PumpView> pumps = pumpFacade.listByDeviceId(summary.id(), state);
        return new DeviceAggregate(summary, state, sensors, pumps);
    }

}


