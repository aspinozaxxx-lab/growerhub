package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "zigbee_bridge_snapshots")
public class ZigbeeBridgeSnapshotEntity {
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "base_topic", nullable = false)
    private String baseTopic;

    @Column(name = "state")
    private String state;

    @Column(name = "info_json", columnDefinition = "TEXT")
    private String infoJson;

    @Column(name = "devices_json", columnDefinition = "TEXT")
    private String devicesJson;

    @Column(name = "coordinator_ieee_address")
    private String coordinatorIeeeAddress;

    @Column(name = "coordinator_json", columnDefinition = "TEXT")
    private String coordinatorJson;

    @Column(name = "permit_join")
    private Boolean permitJoin;

    @Column(name = "permit_join_end")
    private Long permitJoinEnd;

    @Column(name = "version")
    private String version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ZigbeeBridgeSnapshotEntity() {
    }

    public static ZigbeeBridgeSnapshotEntity create(String baseTopic, LocalDateTime now) {
        ZigbeeBridgeSnapshotEntity entity = new ZigbeeBridgeSnapshotEntity();
        entity.id = SINGLETON_ID;
        entity.baseTopic = baseTopic;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public String getBaseTopic() {
        return baseTopic;
    }

    public void setBaseTopic(String baseTopic) {
        this.baseTopic = baseTopic;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getInfoJson() {
        return infoJson;
    }

    public void setInfoJson(String infoJson) {
        this.infoJson = infoJson;
    }

    public String getDevicesJson() {
        return devicesJson;
    }

    public void setDevicesJson(String devicesJson) {
        this.devicesJson = devicesJson;
    }

    public String getCoordinatorIeeeAddress() {
        return coordinatorIeeeAddress;
    }

    public void setCoordinatorIeeeAddress(String coordinatorIeeeAddress) {
        this.coordinatorIeeeAddress = coordinatorIeeeAddress;
    }

    public String getCoordinatorJson() {
        return coordinatorJson;
    }

    public void setCoordinatorJson(String coordinatorJson) {
        this.coordinatorJson = coordinatorJson;
    }

    public Boolean getPermitJoin() {
        return permitJoin;
    }

    public void setPermitJoin(Boolean permitJoin) {
        this.permitJoin = permitJoin;
    }

    public Long getPermitJoinEnd() {
        return permitJoinEnd;
    }

    public void setPermitJoinEnd(Long permitJoinEnd) {
        this.permitJoinEnd = permitJoinEnd;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
