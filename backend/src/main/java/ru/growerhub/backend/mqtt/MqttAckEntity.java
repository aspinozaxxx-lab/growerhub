package ru.growerhub.backend.mqtt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "mqtt_ack",
    indexes = {
        @Index(name = "ix_mqtt_ack_device_id", columnList = "device_id"),
        @Index(name = "ix_mqtt_ack_expires_at", columnList = "expires_at"),
        @Index(name = "ix_mqtt_ack_id", columnList = "id"),
        @Index(name = "ix_mqtt_ack_received_at", columnList = "received_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_mqtt_ack_correlation_id", columnNames = "correlation_id")
    }
)
public class MqttAckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "status", nullable = true)
    private String status;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    protected MqttAckEntity() {
    }

    public static MqttAckEntity create() {
        return new MqttAckEntity();
    }

    public Integer getId() {
        return id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
