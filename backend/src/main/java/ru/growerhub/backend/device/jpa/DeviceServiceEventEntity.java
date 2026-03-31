package ru.growerhub.backend.device.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import ru.growerhub.backend.device.contract.DeviceServiceEventType;

@Entity
@Table(
    name = "device_service_events",
    indexes = {
        @Index(name = "ix_device_service_events_device_received", columnList = "device_id, received_at")
    }
)
public class DeviceServiceEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "device_id", nullable = false)
    private Integer deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private DeviceServiceEventType eventType;

    @Column(name = "sensor_scope", nullable = true)
    private String sensorScope;

    @Column(name = "sensor_type", nullable = true)
    private String sensorType;

    @Column(name = "channel", nullable = true)
    private Integer channel;

    @Column(name = "failure_id", nullable = true)
    private String failureId;

    @Column(name = "error_code", nullable = true)
    private String errorCode;

    @Column(name = "event_at", nullable = true)
    private LocalDateTime eventAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "payload_json", nullable = true, columnDefinition = "TEXT")
    private String payloadJson;

    protected DeviceServiceEventEntity() {
    }

    public static DeviceServiceEventEntity create() {
        return new DeviceServiceEventEntity();
    }

    public Integer getId() {
        return id;
    }

    public Integer getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceServiceEventType getEventType() {
        return eventType;
    }

    public void setEventType(DeviceServiceEventType eventType) {
        this.eventType = eventType;
    }

    public String getSensorScope() {
        return sensorScope;
    }

    public void setSensorScope(String sensorScope) {
        this.sensorScope = sensorScope;
    }

    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public String getFailureId() {
        return failureId;
    }

    public void setFailureId(String failureId) {
        this.failureId = failureId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
