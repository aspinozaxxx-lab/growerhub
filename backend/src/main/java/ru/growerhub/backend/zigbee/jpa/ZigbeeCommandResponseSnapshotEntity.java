package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "zigbee_command_response_snapshots")
public class ZigbeeCommandResponseSnapshotEntity {
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "coordinator_id", nullable = false)
    private Integer coordinatorId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "status")
    private String status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ZigbeeCommandResponseSnapshotEntity() {
    }

    public static ZigbeeCommandResponseSnapshotEntity create(Integer coordinatorId, LocalDateTime now) {
        ZigbeeCommandResponseSnapshotEntity entity = new ZigbeeCommandResponseSnapshotEntity();
        entity.id = coordinatorId;
        entity.coordinatorId = coordinatorId;
        entity.topic = "";
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public Integer getCoordinatorId() {
        return coordinatorId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
