package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "automation_control_channels", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_key"}))
public class ControlChannelEntity {
    @Id public UUID id;
    public Integer userId;
    public Integer resourceId;
    public String targetKey;
    public String bindingFingerprint;
    public UUID coordinatorId;
    public String ieeeAddress;
    public String property;
    public UUID winnerId;
    public int revision;
    public UUID commandId;
    @Column(columnDefinition = "TEXT") public String valueJson;
    public String status;
    public LocalDateTime requestedAt;
    public LocalDateTime dispatchAt;
    public LocalDateTime ackDeadlineAt;
    public LocalDateTime confirmedAt;
    @Column(columnDefinition = "TEXT") public String error;
}
