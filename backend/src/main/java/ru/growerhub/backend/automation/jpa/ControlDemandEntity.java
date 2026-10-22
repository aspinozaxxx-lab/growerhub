package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "automation_control_demands", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "source_key"}))
public class ControlDemandEntity {
    @Id public UUID id;
    public Integer userId;
    public Integer resourceId;
    public String targetKey;
    public String bindingFingerprint;
    public String sourceType;
    public String sourceId;
    public String sourceKey;
    public String sourceName;
    @Column(columnDefinition = "TEXT") public String reason;
    public int revision;
    public int priority;
    @Column(columnDefinition = "TEXT") public String valueJson;
    public String kind;
    public String status;
    public String sourceFingerprint;
    public LocalDateTime requestedAt;
    public LocalDateTime createdAt;
    public Integer durationSeconds;
    public LocalDateTime confirmedAt;
    public LocalDateTime expiresAt;
    public LocalDateTime validUntil;
    public LocalDateTime ackDeadlineAt;
    public boolean interrupted;
    @Column(columnDefinition = "TEXT") public String error;
}
