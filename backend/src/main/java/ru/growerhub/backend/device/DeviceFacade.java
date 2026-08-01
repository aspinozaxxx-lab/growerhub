package ru.growerhub.backend.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.device.DeviceClaimSettings;
import ru.growerhub.backend.common.config.device.DeviceMqttSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.contract.DeviceAggregate;
import ru.growerhub.backend.device.contract.DeviceAckStore;
import ru.growerhub.backend.device.contract.DeviceBrokerCredentialGateway;
import ru.growerhub.backend.device.contract.DeviceClaimRateLimitException;
import ru.growerhub.backend.device.contract.DeviceClaimRejectedException;
import ru.growerhub.backend.device.contract.DeviceFirmwareStatus;
import ru.growerhub.backend.device.contract.DeviceCredential;
import ru.growerhub.backend.device.contract.DeviceMqttCredential;
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
import ru.growerhub.backend.device.jpa.DeviceClaimLimitEntity;
import ru.growerhub.backend.device.jpa.DeviceClaimLimitRepository;
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
    private static final int DEVICE_CREDENTIAL_BYTES = 32;
    private final DeviceRepository deviceRepository;
    private final DeviceClaimLimitRepository deviceClaimLimitRepository;
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
    private final DeviceBrokerCredentialGateway brokerCredentialGateway;
    private final DeviceMqttSettings mqttSettings;
    private final DeviceClaimSettings claimSettings;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceFacade(
            DeviceRepository deviceRepository,
            DeviceClaimLimitRepository deviceClaimLimitRepository,
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
            @Lazy PumpFacade pumpFacade,
            DeviceBrokerCredentialGateway brokerCredentialGateway,
            DeviceMqttSettings mqttSettings,
            DeviceClaimSettings claimSettings
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceClaimLimitRepository = deviceClaimLimitRepository;
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
        this.brokerCredentialGateway = brokerCredentialGateway;
        this.mqttSettings = mqttSettings;
        this.claimSettings = claimSettings;
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
                && device.getDeviceTokenIssuedAt().plusSeconds(mqttSettings.getCredentialCooldownSeconds()).isAfter(now)) {
            throw new DomainException("too_many_requests", "Povtorite vydachu device token pozhe");
        }
        String rawToken = generateCredential();
        if (device.getMqttProvisionedAt() != null) {
            brokerCredentialGateway.rotateDevice(device.getDeviceId(), rawToken);
            device.setMqttProvisionedAt(now);
        }
        device.setDeviceTokenHash(hashDeviceToken(rawToken));
        device.setDeviceTokenIssuedAt(now);
        deviceRepository.save(device);
        return new DeviceCredential(device.getDeviceId(), rawToken, now);
    }

    @Transactional
    public DeviceMqttCredential provisionMqttDevice(String requestedDeviceId, boolean rotate) {
        String deviceId = normalizeDeviceId(requestedDeviceId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = deviceRepository.findByDeviceIdForUpdate(deviceId).orElse(null);
        if (device == null) {
            device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            deviceIngestionService.applyDefaults(device, deviceId);
            device = deviceRepository.saveAndFlush(device);
        } else if (device.getMqttProvisionedAt() != null && !rotate) {
            throw new DomainException("conflict", "Устройство уже подготовлено; для перевыпуска укажите rotate=true");
        }

        if (device.getDeviceTokenIssuedAt() != null
                && device.getDeviceTokenIssuedAt().plusSeconds(mqttSettings.getCredentialCooldownSeconds()).isAfter(now)) {
            throw new DomainException("too_many_requests", "Повторите подготовку устройства позже");
        }

        String password = generateCredential();
        if (device.getMqttProvisionedAt() == null) {
            brokerCredentialGateway.provisionDevice(deviceId, password, mqttSettings.getBrokerRole());
        } else {
            brokerCredentialGateway.rotateDevice(deviceId, password);
        }
        device.setDeviceTokenHash(hashDeviceToken(password));
        device.setDeviceTokenIssuedAt(now);
        device.setMqttProvisionedAt(now);
        deviceRepository.save(device);
        return new DeviceMqttCredential(
                deviceId,
                mqttSettings.getPublicHost(),
                mqttSettings.getPublicPort(),
                mqttSettings.isTls(),
                deviceId,
                password,
                deviceId,
                now
        );
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

    @Transactional(noRollbackFor = DeviceClaimRejectedException.class)
    public DeviceAggregate claimDevice(String requestedDeviceId, Integer userId) {
        if (userId == null) {
            throw new DomainException("unauthorized", "Требуется авторизация");
        }
        String deviceId = normalizeDeviceId(requestedDeviceId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        deviceRepository.lockUserForDeviceClaim(userId);
        DeviceClaimLimitEntity limit = deviceClaimLimitRepository.findById(userId).orElse(null);
        if (limit != null && limit.isRestricted()) {
            consumeRestrictedAttempt(limit, now);
        }

        DeviceEntity device = deviceRepository.findByDeviceIdForUpdate(deviceId).orElse(null);
        if (device == null) {
            recordFailedClaim(limit, userId, now);
            throw new DeviceClaimRejectedException("not_found", "Устройство не найдено");
        }

        Integer ownerId = device.getUserId();
        if (ownerId != null && !ownerId.equals(userId)) {
            recordFailedClaim(limit, userId, now);
            throw new DeviceClaimRejectedException(
                    "conflict",
                    "Устройство уже используется другим пользователем — обратитесь к администратору"
            );
        }
        if (ownerId != null) {
            return buildAggregate(deviceQueryService.buildDeviceSummary(device));
        }

        device.setUserId(userId);
        deviceRepository.save(device);
        deviceClaimLimitRepository.deleteById(userId);
        DeviceSummary summary = deviceQueryService.buildDeviceSummary(device);
        pumpFacade.ensureDefaultPump(summary.id());
        return buildAggregate(summary);
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
        if (device.getMqttProvisionedAt() != null) {
            brokerCredentialGateway.revokeDevice(deviceId, mqttSettings.getBrokerRole());
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

    private String generateCredential() {
        byte[] randomBytes = new byte[DEVICE_CREDENTIAL_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String normalizeDeviceId(String requestedDeviceId) {
        return requestedDeviceId != null ? requestedDeviceId.trim().toUpperCase(Locale.ROOT) : "";
    }

    private void consumeRestrictedAttempt(DeviceClaimLimitEntity limit, LocalDateTime now) {
        LocalDateTime blockedUntil = limit.getBlockedUntil();
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            throw new DeviceClaimRateLimitException(retryAfterSeconds(now, blockedUntil));
        }
        limit.setBlockedUntil(null);

        int attempts = limit.getWindowAttempts();
        LocalDateTime windowStartedAt = limit.getWindowStartedAt();
        if (attempts <= 0 || windowStartedAt == null) {
            limit.setWindowStartedAt(now);
            limit.setWindowAttempts(1);
            limit.setUpdatedAt(now);
            deviceClaimLimitRepository.save(limit);
            return;
        }

        if (attempts == 1) {
            if (!windowStartedAt.plusSeconds(claimSettings.getRestrictedWindowSeconds()).isAfter(now)) {
                limit.setWindowStartedAt(now);
                limit.setWindowAttempts(1);
            } else {
                limit.setWindowAttempts(2);
            }
            limit.setUpdatedAt(now);
            deviceClaimLimitRepository.save(limit);
            return;
        }

        if (windowStartedAt.plusSeconds(claimSettings.getRestrictedWindowSeconds()).isAfter(now)) {
            LocalDateTime retryAt = windowStartedAt.plusSeconds(claimSettings.getRestrictedWindowSeconds());
            throw new DeviceClaimRateLimitException(retryAfterSeconds(now, retryAt));
        }

        LocalDateTime newestAttemptAt = limit.getUpdatedAt();
        if (newestAttemptAt != null
                && newestAttemptAt.plusSeconds(claimSettings.getRestrictedWindowSeconds()).isAfter(now)) {
            limit.setWindowStartedAt(newestAttemptAt);
            limit.setWindowAttempts(2);
        } else {
            limit.setWindowStartedAt(now);
            limit.setWindowAttempts(1);
        }
        limit.setUpdatedAt(now);
        deviceClaimLimitRepository.save(limit);
    }

    private void recordFailedClaim(DeviceClaimLimitEntity limit, Integer userId, LocalDateTime now) {
        DeviceClaimLimitEntity current = limit != null ? limit : DeviceClaimLimitEntity.create(userId, now);
        if (!current.isRestricted()) {
            int failures = current.getFailedAttempts() + 1;
            current.setFailedAttempts(failures);
            if (failures >= claimSettings.getInitialFailureLimit()) {
                current.setRestricted(true);
                current.setBlockedUntil(now.plusSeconds(claimSettings.getBlockSeconds()));
                current.setWindowStartedAt(null);
                current.setWindowAttempts(0);
            }
        }
        current.setUpdatedAt(now);
        deviceClaimLimitRepository.save(current);
    }

    private long retryAfterSeconds(LocalDateTime now, LocalDateTime retryAt) {
        long millis = Duration.between(now, retryAt).toMillis();
        return Math.max(1, (millis + 999) / 1000);
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
