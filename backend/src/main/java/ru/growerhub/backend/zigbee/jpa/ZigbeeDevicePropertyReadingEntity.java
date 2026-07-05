package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "zigbee_device_property_readings",
    indexes = {
        @Index(name = "ix_zigbee_property_readings_ieee_property_ts", columnList = "ieee_address, property, ts"),
        @Index(name = "ix_zigbee_property_readings_friendly_property_ts", columnList = "friendly_name, property, ts")
    }
)
public class ZigbeeDevicePropertyReadingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "state_event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ZigbeeDeviceStateEventEntity stateEvent;

    @ManyToOne
    @JoinColumn(name = "device_snapshot_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ZigbeeDeviceSnapshotEntity deviceSnapshot;

    @Column(name = "ieee_address")
    private String ieeeAddress;

    @Column(name = "friendly_name", nullable = false)
    private String friendlyName;

    @Column(name = "property", nullable = false)
    private String property;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "value_numeric")
    private Double valueNumeric;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ZigbeeDevicePropertyReadingEntity() {
    }

    public static ZigbeeDevicePropertyReadingEntity create() {
        return new ZigbeeDevicePropertyReadingEntity();
    }

    public Integer getId() {
        return id;
    }

    public ZigbeeDeviceStateEventEntity getStateEvent() {
        return stateEvent;
    }

    public void setStateEvent(ZigbeeDeviceStateEventEntity stateEvent) {
        this.stateEvent = stateEvent;
    }

    public ZigbeeDeviceSnapshotEntity getDeviceSnapshot() {
        return deviceSnapshot;
    }

    public void setDeviceSnapshot(ZigbeeDeviceSnapshotEntity deviceSnapshot) {
        this.deviceSnapshot = deviceSnapshot;
    }

    public String getIeeeAddress() {
        return ieeeAddress;
    }

    public void setIeeeAddress(String ieeeAddress) {
        this.ieeeAddress = ieeeAddress;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public Double getValueNumeric() {
        return valueNumeric;
    }

    public void setValueNumeric(Double valueNumeric) {
        this.valueNumeric = valueNumeric;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Boolean valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
