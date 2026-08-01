package ru.growerhub.backend.device.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_claim_limits")
public class DeviceClaimLimitEntity {
    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "restricted", nullable = false)
    private boolean restricted;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "window_started_at")
    private LocalDateTime windowStartedAt;

    @Column(name = "window_attempts", nullable = false)
    private int windowAttempts;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DeviceClaimLimitEntity() {
    }

    public static DeviceClaimLimitEntity create(Integer userId, LocalDateTime now) {
        DeviceClaimLimitEntity entity = new DeviceClaimLimitEntity();
        entity.userId = userId;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getUserId() {
        return userId;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public LocalDateTime getWindowStartedAt() {
        return windowStartedAt;
    }

    public void setWindowStartedAt(LocalDateTime windowStartedAt) {
        this.windowStartedAt = windowStartedAt;
    }

    public int getWindowAttempts() {
        return windowAttempts;
    }

    public void setWindowAttempts(int windowAttempts) {
        this.windowAttempts = windowAttempts;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
