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
import ru.growerhub.backend.device.contract.DeviceServiceEventData;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.DeviceServiceEventMessage;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;

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
    private final MqttMessageLog messageLog;
    private final ZigbeeFacade zigbeeFacade;

    public MqttMessageHandler(
            ObjectMapper objectMapper,
            AckStore ackStore,
            DeviceFacade deviceFacade,
            AckSettings ackSettings,
            DebugSettings debugSettings,
            Clock clock,
            MqttTopicSettings topicSettings,
            MqttMessageLog messageLog,
            ZigbeeFacade zigbeeFacade
    ) {
        this.objectMapper = objectMapper;
        this.ackStore = ackStore;
        this.deviceFacade = deviceFacade;
        this.ackSettings = ackSettings;
        this.debugSettings = debugSettings;
        this.clock = clock;
        this.topicSettings = topicSettings;
        this.messageLog = messageLog;
        this.zigbeeFacade = zigbeeFacade;
    }

    public void handleInboundMessage(String topic, byte[] payload) {
        messageLog.recordInbound(topic, payload, resolveKind(topic));
        if (handleZigbeeMessage(topic, payload)) {
            return;
        }
        if (topic != null && topic.endsWith(topicSettings.getAckSuffix())) {
            handleAckMessage(topic, payload);
        } else if (topic != null && topic.endsWith(topicSettings.getEventsSuffix())) {
            handleEventMessage(topic, payload);
        } else if (topic != null && topic.endsWith(topicSettings.getStateSuffix())) {
            handleStateMessage(topic, payload);
        }
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

    public void handleEventMessage(String topic, byte[] payload) {
        if (debugSettings.isDebug()) {
            logger.info("MQTT DEBUG event topic={} payload={}", topic, safePayload(payload));
        }
        String deviceId = extractDeviceId(topic, topicSettings.getEventsSuffix(), 4);
        if (deviceId == null) {
            logger.warn("MQTT event topic mismatch: {}", topic);
            return;
        }
        DeviceServiceEventMessage event;
        try {
            event = objectMapper.readValue(payload, DeviceServiceEventMessage.class);
        } catch (Exception ex) {
            logger.warn("Ne udalos razobrat event ot {}: {}", deviceId, ex.getMessage());
            return;
        }
        if (event.type() == null || event.type().isBlank()) {
            logger.warn("MQTT event bez type ot {}: {}", deviceId, safePayload(payload));
            return;
        }
        LocalDateTime receivedAt = LocalDateTime.now(clock);
        deviceFacade.handleServiceEvent(deviceId, new DeviceServiceEventData(
                event.type(),
                event.sensorScope(),
                event.sensorType(),
                event.channel(),
                event.failureId(),
                event.errorCode(),
                event.ts(),
                safePayload(payload)
        ), receivedAt);
        logger.info("MQTT event stored for {} type={}", deviceId, event.type());
    }

    private LocalDateTime resolveExpiresAt(LocalDateTime receivedAt) {
        int ttl = ackSettings.getTtlSeconds();
        if (ttl <= 0) {
            return null;
        }
        return receivedAt.plusSeconds(ttl);
    }

    private String resolveKind(String topic) {
        if (topic == null) {
            return "raw";
        }
        if (topic.endsWith(topicSettings.getAckSuffix())) {
            return "ack";
        }
        if (topic.endsWith(topicSettings.getEventsSuffix())) {
            return "event";
        }
        if (topic.endsWith(topicSettings.getStateSuffix())) {
            return "state";
        }
        String zigbeeBase = topicSettings.getZigbeeBase();
        if (zigbeeBase != null && !zigbeeBase.isBlank() && topic.startsWith(zigbeeBase + "/")) {
            return "zigbee";
        }
        return "raw";
    }

    private boolean handleZigbeeMessage(String topic, byte[] payload) {
        String zigbeeBase = topicSettings.getZigbeeBase();
        if (topic == null || zigbeeBase == null || zigbeeBase.isBlank() || !topic.startsWith(zigbeeBase + "/")) {
            return false;
        }

        String relativeTopic = topic.substring(zigbeeBase.length() + 1);
        ZigbeeMqttMessageType type = null;
        String friendlyName = null;

        if ("bridge/state".equals(relativeTopic)) {
            type = ZigbeeMqttMessageType.BRIDGE_STATE;
        } else if ("bridge/info".equals(relativeTopic)) {
            type = ZigbeeMqttMessageType.BRIDGE_INFO;
        } else if ("bridge/devices".equals(relativeTopic)) {
            type = ZigbeeMqttMessageType.BRIDGE_DEVICES;
        } else if (relativeTopic.startsWith("bridge/response/")) {
            type = ZigbeeMqttMessageType.COMMAND_RESPONSE;
        } else if (!relativeTopic.startsWith("bridge/")) {
            if (relativeTopic.endsWith("/availability")) {
                friendlyName = relativeTopic.substring(0, relativeTopic.length() - "/availability".length());
                type = ZigbeeMqttMessageType.DEVICE_AVAILABILITY;
            } else if (!relativeTopic.contains("/")) {
                friendlyName = relativeTopic;
                type = ZigbeeMqttMessageType.DEVICE_STATE;
            }
        }

        if (type == null) {
            return true;
        }

        String rawPayload = safePayload(payload);
        Object parsedPayload = parseJson(rawPayload);
        zigbeeFacade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(
                type,
                topic,
                relativeTopic,
                friendlyName,
                rawPayload,
                parsedPayload,
                LocalDateTime.now(clock)
        ));
        return true;
    }

    private Object parseJson(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return rawPayload;
        }
        try {
            return objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception ex) {
            return rawPayload;
        }
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
                    state.manualWatering().correlationId(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        DeviceShadowState.AirState air = state.air() != null
                ? new DeviceShadowState.AirState(
                        state.air().available(),
                        state.air().temperature(),
                        state.air().humidity(),
                        state.air().status())
                : null;
        DeviceShadowState.SoilState soil = state.soil() != null
                ? new DeviceShadowState.SoilState(state.soil().ports() != null
                ? state.soil().ports().stream()
                .map(port -> port != null
                        ? new DeviceShadowState.SoilPort(port.port(), port.detected(), port.percent(), port.status())
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
