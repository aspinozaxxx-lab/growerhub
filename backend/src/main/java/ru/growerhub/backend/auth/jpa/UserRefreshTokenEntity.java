﻿package ru.growerhub.backend.auth.jpa;

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
    name = "user_refresh_tokens",
    indexes = {
        @Index(name = "ix_user_refresh_tokens_user_id", columnList = "user_id"),
        @Index(name = "ix_user_refresh_tokens_expires_at", columnList = "expires_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_refresh_tokens_token_hash", columnNames = "token_hash")
    }
)
public class UserRefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = true)
    private LocalDateTime revokedAt;

    @Lob
    @Column(name = "user_agent", nullable = true)
    private String userAgent;

    @Column(name = "ip", nullable = true)
    private String ip;

    protected UserRefreshTokenEntity() {
    }

    public static UserRefreshTokenEntity create(
            Integer userId,
            String tokenHash,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            String userAgent,
            String ip
    ) {
        UserRefreshTokenEntity record = new UserRefreshTokenEntity();
        record.userId = userId;
        record.tokenHash = tokenHash;
        record.createdAt = createdAt;
        record.expiresAt = expiresAt;
        record.revokedAt = null;
        record.userAgent = userAgent;
        record.ip = ip;
        return record;
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}


