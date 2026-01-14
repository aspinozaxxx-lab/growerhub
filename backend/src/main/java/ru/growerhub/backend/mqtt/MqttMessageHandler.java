﻿package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.AckSettings;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;

@Component
public class MqttMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final AckStore ackStore;
    private final DeviceFacade deviceFacade;
    private final AckSettings ackSettings;
    private final DebugSettings debugSettings;
    private final Clock clock;
    private final MqttTopicSettings topicSettings;

    public MqttMessageHandler(
            ObjectMapper objectMapper,
            AckStore ackStore,
            DeviceFacade deviceFacade,
            AckSettings ackSettings,
            DebugSettings debugSettings,
            Clock clock,
            MqttTopicSettings topicSettings
    ) {
        this.objectMapper = objectMapper;
        this.ackStore = ackStore;
        this.deviceFacade = deviceFacade;
        this.ackSettings = ackSettings;
        this.debugSettings = debugSettings;
        this.clock = clock;
        this.topicSettings = topicSettings;
    }

    public void handleStateMessage(String topic, byte[] payload) {
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG state topic={} payload={}", topic, safePayload(payload));
        }
        String deviceId = extractDeviceId(topic, topicSettings.getStateSuffix(), 4);
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
        deviceFacade.handleState(deviceId, toShadowState(state), now);
        logger.info("MQTT state updated for {}", deviceId);
    }

    public void handleAckMessage(String topic, byte[] payload) {
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG ack topic={} payload={}", topic, safePayload(payload));
        }
        String deviceId = extractDeviceId(topic, topicSettings.getAckSuffix(), 5);
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
        deviceFacade.handleAck(deviceId, correlationId, result, ack.status(), payloadMap, receivedAt, expiresAt);
        logger.info("MQTT ack stored for {} correlation_id={}", deviceId, correlationId);
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

    private DeviceShadowState toShadowState(DeviceState state) {
        if (state == null) {
            return null;
        }
        DeviceShadowState.ManualWateringState manual = null;
        if (state.manualWatering() != null) {
            manual = new DeviceShadowState.ManualWateringState(
                    state.manualWatering().status(),
                    state.manualWatering().durationS(),
                    state.manualWatering().startedAt(),
                    state.manualWatering().remainingS(),
                    state.manualWatering().correlationId()
            );
        }
        DeviceShadowState.AirState air = state.air() != null
                ? new DeviceShadowState.AirState(state.air().available(), state.air().temperature(), state.air().humidity())
                : null;
        DeviceShadowState.SoilState soil = state.soil() != null
                ? new DeviceShadowState.SoilState(state.soil().ports() != null
                ? state.soil().ports().stream()
                .map(port -> port != null
                        ? new DeviceShadowState.SoilPort(port.port(), port.detected(), port.percent())
                        : null)
                .toList()
                : null)
                : null;
        DeviceShadowState.RelayState light = state.light() != null
                ? new DeviceShadowState.RelayState(state.light().status())
                : null;
        DeviceShadowState.RelayState pump = state.pump() != null
                ? new DeviceShadowState.RelayState(state.pump().status())
                : null;
        DeviceShadowState.ScenariosState scenarios = null;
        if (state.scenarios() != null) {
            DeviceShadowState.ScenarioState waterTime = state.scenarios().waterTime() != null
                    ? new DeviceShadowState.ScenarioState(state.scenarios().waterTime().enabled())
                    : null;
            DeviceShadowState.ScenarioState waterMoisture = state.scenarios().waterMoisture() != null
                    ? new DeviceShadowState.ScenarioState(state.scenarios().waterMoisture().enabled())
                    : null;
            DeviceShadowState.ScenarioState lightSchedule = state.scenarios().lightSchedule() != null
                    ? new DeviceShadowState.ScenarioState(state.scenarios().lightSchedule().enabled())
                    : null;
            scenarios = new DeviceShadowState.ScenariosState(waterTime, waterMoisture, lightSchedule);
        }
        return new DeviceShadowState(
                manual,
                state.fwVer(),
                state.soilMoisture(),
                state.airTemperature(),
                state.airHumidity(),
                air,
                soil,
                light,
                pump,
                scenarios
        );
    }
}
