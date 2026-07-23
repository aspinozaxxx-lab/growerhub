package ru.growerhub.backend.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.contract.DeviceAggregate;
import ru.growerhub.backend.device.contract.DeviceAckStore;
import ru.growerhub.backend.device.contract.DeviceFirmwareStatus;
import ru.growerhub.backend.device.contract.DeviceCredential;
import ru.growerhub.backend.device.contract.DeviceServiceEventData;
import ru.growerhub.backend.device.contract.DeviceServiceEventView;
import ru.growerhub.backend.device.contract.DeviceSettingsData;
import ru.growerhub.backend.device.contract.DeviceSettingsUpdate;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.device.engine.AckCleanupService;
import ru.growerhub.backend.device.engine.DeviceAckService;
import ru.growerhub.backend.device.engine.DeviceIngestionService;
import ru.growerhub.backend.device.engine.DeviceQueryService;
import ru.growerhub.backend.device.engine.DeviceServiceEventService;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.device.jpa.MqttAckRepository;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.contract.SensorMeasurement;
import ru.growerhub.backend.sensor.contract.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorView;

@Service
public class DeviceFacade {
    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final DeviceIngestionService deviceIngestionService;
    private final DeviceQueryService deviceQueryService;
    private final DeviceShadowStore shadowStore;
    private final DeviceAckService ackService;
    private final AckCleanupService ackCleanupService;
    private final MqttAckRepository mqttAckRepository;
    private final DeviceAckStore ackStore;
    private final DeviceServiceEventService deviceServiceEventService;
    private final SensorFacade sensorFacade;
    private final PlantFacade plantFacade;
    private final PumpFacade pumpFacade;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceFacade(
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            DeviceIngestionService deviceIngestionService,
            DeviceQueryService deviceQueryService,
            DeviceShadowStore shadowStore,
            DeviceAckService ackService,
            AckCleanupService ackCleanupService,
            MqttAckRepository mqttAckRepository,
            DeviceAckStore ackStore,
            DeviceServiceEventService deviceServiceEventService,
            SensorFacade sensorFacade,
            PlantFacade plantFacade,
            @Lazy PumpFacade pumpFacade
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.deviceIngestionService = deviceIngestionService;
        this.deviceQueryService = deviceQueryService;
        this.shadowStore = shadowStore;
        this.ackService = ackService;
        this.ackCleanupService = ackCleanupService;
        this.mqttAckRepository = mqttAckRepository;
        this.ackStore = ackStore;
        this.deviceServiceEventService = deviceServiceEventService;
        this.sensorFacade = sensorFacade;
        this.plantFacade = plantFacade;
        this.pumpFacade = pumpFacade;
    }

    public Integer findDeviceId(String deviceId) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        return device != null ? device.getId() : null;
    }

    @Transactional(readOnly = true)
    public boolean authenticateDevice(String deviceId, String rawToken) {
        if (deviceId == null || deviceId.isBlank() || rawToken == null || rawToken.isBlank()) {
            return false;
        }
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null || device.getDeviceTokenHash() == null) {
            return false;
        }
        byte[] expected = device.getDeviceTokenHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hashDeviceToken(rawToken).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    @Transactional(readOnly = true)
    public boolean canUserAccessDevice(String deviceId, Integer userId, boolean admin) {
        if (deviceId == null || userId == null) {
            return false;
        }
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        return device != null && (admin || userId.equals(device.getUserId()));
    }

    @Transactional
    public DeviceCredential rotateDeviceCredential(Integer devicePk, Integer userId, boolean admin) {
        DeviceEntity device = requireDevice(devicePk);
        if (!admin && (userId == null || !userId.equals(device.getUserId()))) {
            throw new DomainException("not_found", "Ustrojstvo ne naideno");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (device.getDeviceTokenIssuedAt() != null
                && device.getDeviceTokenIssuedAt().plusSeconds(30).isAfter(now)) {
            throw new DomainException("too_many_requests", "Povtorite vydachu device token pozhe");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        device.setDeviceTokenHash(hashDeviceToken(rawToken));
        device.setDeviceTokenIssuedAt(now);
        deviceRepository.save(device);
        return new DeviceCredential(device.getDeviceId(), rawToken, now);
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
    public void handleState(String deviceId, DeviceShadowState state, LocalDateTime now) {
        List<SensorMeasurement> measurements = deviceIngestionService.handleState(deviceId, state, now);
        // Translitem: pri auto-provision garantiruem default pump dlya novogo device.
        Integer devicePk = findDeviceId(deviceId);
        if (devicePk != null) {
            pumpFacade.ensureDefaultPump(devicePk);
            pumpFacade.recordStateByDeviceId(devicePk, state, now);
        }
        pumpFacade.finalizeWateringByDeviceId(deviceId, now);
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
    public void handleServiceEvent(String deviceId, DeviceServiceEventData event, LocalDateTime receivedAt) {
        deviceServiceEventService.recordEvent(deviceId, event, receivedAt);
    }

    // Translitem: facade-orientirovannaya ochistka ACK v tranzakcii dlya scheduled worker.
    @Transactional
    public int cleanupExpiredAcks() {
        return ackCleanupService.cleanupExpired();
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

    @Transactional(noRollbackFor = RuntimeException.class)
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

    @Transactional(readOnly = true)
    public Map<Integer, List<DeviceServiceEventView>> listRecentServiceEventsByDeviceIds(List<Integer> deviceIds, int limitPerDevice) {
        return deviceServiceEventService.listRecentByDeviceIds(deviceIds, limitPerDevice);
    }

    @Transactional
    public DeviceSummary assignToUser(Integer deviceId, Integer userId) {
        DeviceEntity device = requireDevice(deviceId);
        Integer ownerId = device.getUserId();
        if (ownerId == null) {
            device.setUserId(userId);
        } else if (!ownerId.equals(userId)) {
            throw new DomainException("bad_request", "ustrojstvo uzhe privyazano k drugomu polzovatelju");
        }
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceSummary unassignForUser(Integer deviceId, Integer userId, boolean isAdmin) {
        DeviceEntity device = requireDevice(deviceId);
        if (!isAdmin) {
            Integer ownerId = device.getUserId();
            if (ownerId == null || !ownerId.equals(userId)) {
                throw new DomainException("forbidden", "nedostatochno prav dlya otvyazki etogo ustrojstva");
            }
        }
        device.setUserId(null);
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceAggregate assignToUserAggregate(Integer deviceId, Integer userId) {
        DeviceSummary summary = assignToUser(deviceId, userId);
        if (summary != null) {
            pumpFacade.ensureDefaultPump(summary.id());
        }
        return buildAggregate(summary);
    }

    @Transactional
    public DeviceAggregate unassignForUserAggregate(Integer deviceId, Integer userId, boolean isAdmin) {
        DeviceSummary summary = unassignForUser(deviceId, userId, isAdmin);
        if (summary != null) {
            pumpFacade.ensureDefaultPump(summary.id());
        }
        return buildAggregate(summary);
    }

    @Transactional
    public DeviceSummary adminAssign(Integer deviceId, Integer userId) {
        DeviceEntity device = requireDevice(deviceId);
        device.setUserId(userId);
        deviceRepository.save(device);
        return deviceQueryService.buildDeviceSummary(device);
    }

    @Transactional
    public DeviceSummary adminUnassign(Integer deviceId) {
        DeviceEntity device = requireDevice(deviceId);
        device.setUserId(null);
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
            deviceServiceEventService.deleteByDeviceId(id);
        }
        deviceStateLastRepository.deleteByDeviceId(deviceId);
        mqttAckRepository.deleteByDeviceId(deviceId);
        deviceRepository.delete(device);
        shadowStore.remove(deviceId);
        ackStore.remove(deviceId);
    }

    @Transactional
    public void unassignDevicesForUser(Integer userId) {
        if (userId == null) {
            return;
        }
        List<DeviceEntity> devices = deviceRepository.findAllByUserId(userId);
        for (DeviceEntity device : devices) {
            device.setUserId(null);
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

    private String hashDeviceToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
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
