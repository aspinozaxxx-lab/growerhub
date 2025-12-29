package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.MqttAckEntity;
import ru.growerhub.backend.db.MqttAckRepository;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;

@Component
public class MqttMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MqttMessageHandler.class);
    private static final String STATE_SUFFIX = "/state";
    private static final String ACK_SUFFIX = "/state/ack";
    private static final double DEFAULT_TARGET_MOISTURE = 40.0;
    private static final int DEFAULT_WATERING_DURATION = 30;
    private static final int DEFAULT_WATERING_TIMEOUT = 300;
    private static final int DEFAULT_LIGHT_ON_HOUR = 6;
    private static final int DEFAULT_LIGHT_OFF_HOUR = 22;
    private static final int DEFAULT_LIGHT_DURATION = 16;
    private static final String DEFAULT_CURRENT_VERSION = "1.0.0";
    private static final String DEFAULT_LATEST_VERSION = "1.0.0";
    private static final boolean DEFAULT_UPDATE_AVAILABLE = false;

    private final ObjectMapper objectMapper;
    private final DeviceShadowStore shadowStore;
    private final AckStore ackStore;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final MqttAckRepository mqttAckRepository;
    private final DeviceRepository deviceRepository;
    private final AckSettings ackSettings;
    private final DebugSettings debugSettings;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public MqttMessageHandler(
            ObjectMapper objectMapper,
            DeviceShadowStore shadowStore,
            AckStore ackStore,
            DeviceStateLastRepository deviceStateLastRepository,
            MqttAckRepository mqttAckRepository,
            DeviceRepository deviceRepository,
            PlatformTransactionManager transactionManager,
            AckSettings ackSettings,
            DebugSettings debugSettings,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.shadowStore = shadowStore;
        this.ackStore = ackStore;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.mqttAckRepository = mqttAckRepository;
        this.deviceRepository = deviceRepository;
        this.ackSettings = ackSettings;
        this.debugSettings = debugSettings;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void handleStateMessage(String topic, byte[] payload) {
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG state topic={} payload={}", topic, safePayload(payload));
        }
        String deviceId = extractDeviceId(topic, STATE_SUFFIX, 4);
        if (deviceId == null) {
            logger.warn("MQTT state topic mismatch: {}", topic);
            return;
        }
        DeviceState state;
        try {
            state = objectMapper.readValue(payload, DeviceState.class);
        } catch (Exception ex) {
            logger.warn("Ne udalos razobrat state ot {}: {}", deviceId, ex.getMessage());
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ensureDeviceExists(deviceId, now);
        shadowStore.updateFromState(deviceId, state);
        upsertDeviceState(deviceId, state, now);
        logger.info("MQTT state updated for {}", deviceId);
    }

    @Transactional
    public void handleAckMessage(String topic, byte[] payload) {
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG ack topic={} payload={}", topic, safePayload(payload));
        }
        String deviceId = extractDeviceId(topic, ACK_SUFFIX, 5);
        if (deviceId == null) {
            logger.warn("MQTT ack topic mismatch: {}", topic);
            return;
        }
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            logger.warn("Ne udalos razobrat ACK ot {}: {}", deviceId, ex.getMessage());
            return;
        }
        String correlationId = asString(payloadMap.get("correlation_id"));
        String result = asString(payloadMap.get("result"));
        if (correlationId == null || result == null) {
            logger.warn("ACK payload bez correlation_id/result: {}", payloadMap);
            return;
        }
        ManualWateringAck ack = new ManualWateringAck(
                correlationId,
                result,
                asString(payloadMap.get("reason")),
                asString(payloadMap.get("status"))
        );
        ackStore.put(deviceId, ack);

        LocalDateTime receivedAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = resolveExpiresAt(receivedAt);
        upsertAck(deviceId, correlationId, result, ack.status(), payloadMap, receivedAt, expiresAt);
        touchLastSeen(deviceId, receivedAt);
        logger.info("MQTT ack stored for {} correlation_id={}", deviceId, correlationId);
    }

    private void upsertDeviceState(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        DeviceStateLastEntity record = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        if (record == null) {
            record = DeviceStateLastEntity.create();
            record.setDeviceId(deviceId);
        }
        record.setStateJson(payload);
        record.setUpdatedAt(updatedAt);
        deviceStateLastRepository.save(record);
    }

    private void upsertAck(
            String deviceId,
            String correlationId,
            String result,
            String status,
            Map<String, Object> payloadMap,
            LocalDateTime receivedAt,
            LocalDateTime expiresAt
    ) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        MqttAckEntity record = mqttAckRepository.findByCorrelationId(correlationId).orElse(null);
        if (record == null) {
            record = MqttAckEntity.create();
            record.setCorrelationId(correlationId);
        }
        record.setDeviceId(deviceId);
        record.setResult(result);
        record.setStatus(status);
        record.setPayloadJson(payloadJson);
        record.setReceivedAt(receivedAt);
        record.setExpiresAt(expiresAt);
        mqttAckRepository.save(record);
    }

    private void ensureDeviceExists(String deviceId, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            if (deviceRepository.findByDeviceId(deviceId).isPresent()) {
                return;
            }
            DeviceEntity device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName("Watering Device " + deviceId);
            device.setLastSeen(now);
            applyDefaults(device, deviceId);
            try {
                deviceRepository.saveAndFlush(device);
            } catch (DataIntegrityViolationException ex) {
                status.setRollbackOnly();
            }
        });
    }

    private void applyDefaults(DeviceEntity device, String deviceId) {
        if (device.getName() == null) {
            device.setName("Watering Device " + deviceId);
        }
        if (device.getSoilMoisture() == null) {
            device.setSoilMoisture(0.0);
        }
        if (device.getAirTemperature() == null) {
            device.setAirTemperature(0.0);
        }
        if (device.getAirHumidity() == null) {
            device.setAirHumidity(0.0);
        }
        if (device.getIsWatering() == null) {
            device.setIsWatering(false);
        }
        if (device.getIsLightOn() == null) {
            device.setIsLightOn(false);
        }
        if (device.getTargetMoisture() == null) {
            device.setTargetMoisture(DEFAULT_TARGET_MOISTURE);
        }
        if (device.getWateringDuration() == null) {
            device.setWateringDuration(DEFAULT_WATERING_DURATION);
        }
        if (device.getWateringTimeout() == null) {
            device.setWateringTimeout(DEFAULT_WATERING_TIMEOUT);
        }
        if (device.getLightOnHour() == null) {
            device.setLightOnHour(DEFAULT_LIGHT_ON_HOUR);
        }
        if (device.getLightOffHour() == null) {
            device.setLightOffHour(DEFAULT_LIGHT_OFF_HOUR);
        }
        if (device.getLightDuration() == null) {
            device.setLightDuration(DEFAULT_LIGHT_DURATION);
        }
        if (device.getCurrentVersion() == null) {
            device.setCurrentVersion(DEFAULT_CURRENT_VERSION);
        }
        if (device.getLatestVersion() == null) {
            device.setLatestVersion(DEFAULT_LATEST_VERSION);
        }
        if (device.getUpdateAvailable() == null) {
            device.setUpdateAvailable(DEFAULT_UPDATE_AVAILABLE);
        }
    }

    private void touchLastSeen(String deviceId, LocalDateTime now) {
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

    private LocalDateTime resolveExpiresAt(LocalDateTime receivedAt) {
        int ttl = ackSettings.getTtlSeconds();
        if (ttl <= 0) {
            return null;
        }
        return receivedAt.plusSeconds(ttl);
    }

    private String extractDeviceId(String topic, String suffix, int expectedParts) {
        if (topic == null || !topic.endsWith(suffix)) {
            return null;
        }
        String[] parts = topic.split("/");
        if (parts.length != expectedParts) {
            return null;
        }
        if (!"gh".equals(parts[0]) || !"dev".equals(parts[1])) {
            return null;
        }
        String deviceId = parts[2];
        return deviceId == null || deviceId.isBlank() ? null : deviceId;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String safePayload(byte[] payload) {
        if (payload == null) {
            return "";
        }
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }
}
