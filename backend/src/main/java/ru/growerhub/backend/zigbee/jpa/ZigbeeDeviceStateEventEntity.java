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
    name = "zigbee_device_state_events",
    indexes = {
        @Index(name = "ix_zigbee_state_events_ieee_ts", columnList = "ieee_address, ts"),
        @Index(name = "ix_zigbee_state_events_friendly_ts", columnList = "friendly_name, ts")
    }
)
public class ZigbeeDeviceStateEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "device_snapshot_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ZigbeeDeviceSnapshotEntity deviceSnapshot;

    @Column(name = "ieee_address")
    private String ieeeAddress;

    @Column(name = "friendly_name", nullable = false)
    private String friendlyName;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "raw_state_json", nullable = false, columnDefinition = "TEXT")
    private String rawStateJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ZigbeeDeviceStateEventEntity() {
    }

    public static ZigbeeDeviceStateEventEntity create() {
        return new ZigbeeDeviceStateEventEntity();
    }

    public Integer getId() {
        return id;
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

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public String getRawStateJson() {
        return rawStateJson;
    }

    public void setRawStateJson(String rawStateJson) {
        this.rawStateJson = rawStateJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
