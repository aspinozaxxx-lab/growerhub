package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "zigbee_device_snapshots",
    indexes = {
        @Index(name = "ix_zigbee_device_snapshots_coordinator_friendly", columnList = "coordinator_id,friendly_name")
    }
)
public class ZigbeeDeviceSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "coordinator_id", nullable = false)
    private Integer coordinatorId;

    @Column(name = "ieee_address")
    private String ieeeAddress;

    @Column(name = "friendly_name", nullable = false)
    private String friendlyName;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "supported")
    private Boolean supported;

    @Column(name = "disabled")
    private Boolean disabled;

    @Column(name = "coordinator", nullable = false)
    private boolean coordinator;

    @Column(name = "bridge_device_json", columnDefinition = "TEXT")
    private String bridgeDeviceJson;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "history_checkpoint_json", columnDefinition = "TEXT")
    private String historyCheckpointJson;

    @Column(name = "availability")
    private String availability;

    @Column(name = "last_state_at")
    private LocalDateTime lastStateAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ZigbeeDeviceSnapshotEntity() {
    }

    public static ZigbeeDeviceSnapshotEntity create(Integer coordinatorId, String friendlyName, LocalDateTime now) {
        ZigbeeDeviceSnapshotEntity entity = new ZigbeeDeviceSnapshotEntity();
        entity.coordinatorId = coordinatorId;
        entity.friendlyName = friendlyName;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public Integer getCoordinatorId() {
        return coordinatorId;
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

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public Boolean getSupported() {
        return supported;
    }

    public void setSupported(Boolean supported) {
        this.supported = supported;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isCoordinator() {
        return coordinator;
    }

    public void setCoordinator(boolean coordinator) {
        this.coordinator = coordinator;
    }

    public String getBridgeDeviceJson() {
        return bridgeDeviceJson;
    }

    public void setBridgeDeviceJson(String bridgeDeviceJson) {
        this.bridgeDeviceJson = bridgeDeviceJson;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public String getHistoryCheckpointJson() {
        return historyCheckpointJson;
    }

    public void setHistoryCheckpointJson(String historyCheckpointJson) {
        this.historyCheckpointJson = historyCheckpointJson;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public LocalDateTime getLastStateAt() {
        return lastStateAt;
    }

    public void setLastStateAt(LocalDateTime lastStateAt) {
        this.lastStateAt = lastStateAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
