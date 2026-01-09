package ru.growerhub.backend.device.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeviceAckService {
    private final MqttAckRepository mqttAckRepository;
    private final ObjectMapper objectMapper;

    public DeviceAckService(MqttAckRepository mqttAckRepository, ObjectMapper objectMapper) {
        this.mqttAckRepository = mqttAckRepository;
        this.objectMapper = objectMapper;
    }

    public void upsertAck(
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
}
