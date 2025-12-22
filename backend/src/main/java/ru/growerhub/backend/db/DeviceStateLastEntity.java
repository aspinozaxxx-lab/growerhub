package ru.growerhub.backend.db;

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
    name = "device_state_last",
    indexes = {
        @Index(name = "ix_device_state_last_id", columnList = "id"),
        @Index(name = "ix_device_state_last_updated_at", columnList = "updated_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_device_state_last_device_id", columnNames = "device_id")
    }
)
public class DeviceStateLastEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Lob
    @Column(name = "state_json", nullable = false)
    // TODO: map JSON field properly.
    private String stateJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DeviceStateLastEntity() {
    }

    public Integer getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getStateJson() {
        return stateJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
