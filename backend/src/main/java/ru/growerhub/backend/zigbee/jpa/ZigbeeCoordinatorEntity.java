package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorStatus;

@Entity
@Table(
        name = "zigbee_coordinators",
        indexes = @Index(name = "ix_zigbee_coordinators_user_status", columnList = "user_id,status"),
        uniqueConstraints = {
            @UniqueConstraint(name = "ux_zigbee_coordinators_public_id", columnNames = "public_id"),
            @UniqueConstraint(name = "ux_zigbee_coordinators_mqtt_username", columnNames = "mqtt_username"),
            @UniqueConstraint(name = "ux_zigbee_coordinators_base_topic", columnNames = "base_topic")
        }
)
public class ZigbeeCoordinatorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "mqtt_username", nullable = false)
    private String mqttUsername;

    @Column(name = "base_topic", nullable = false)
    private String baseTopic;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "first_device_seen_at")
    private LocalDateTime firstDeviceSeenAt;

    @Column(name = "credential_issued_at", nullable = false)
    private LocalDateTime credentialIssuedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ZigbeeCoordinatorEntity() {
    }

    public static ZigbeeCoordinatorEntity create(
            UUID publicId,
            Integer userId,
            String name,
            String mqttUsername,
            String baseTopic,
            LocalDateTime now
    ) {
        ZigbeeCoordinatorEntity entity = new ZigbeeCoordinatorEntity();
        entity.publicId = publicId;
        entity.userId = userId;
        entity.name = name;
        entity.mqttUsername = mqttUsername;
        entity.baseTopic = baseTopic;
        entity.status = ZigbeeCoordinatorStatus.PROVISIONING.name();
        entity.credentialIssuedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getBaseTopic() {
        return baseTopic;
    }

    public ZigbeeCoordinatorStatus getStatus() {
        return ZigbeeCoordinatorStatus.valueOf(status);
    }

    public void setStatus(ZigbeeCoordinatorStatus status) {
        this.status = status.name();
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }

    public LocalDateTime getFirstDeviceSeenAt() {
        return firstDeviceSeenAt;
    }

    public void setFirstDeviceSeenAt(LocalDateTime firstDeviceSeenAt) {
        this.firstDeviceSeenAt = firstDeviceSeenAt;
    }

    public LocalDateTime getCredentialIssuedAt() {
        return credentialIssuedAt;
    }

    public void setCredentialIssuedAt(LocalDateTime credentialIssuedAt) {
        this.credentialIssuedAt = credentialIssuedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
