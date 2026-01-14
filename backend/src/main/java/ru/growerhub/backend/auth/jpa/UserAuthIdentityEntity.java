﻿package ru.growerhub.backend.auth.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_auth_identities",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_auth_identities_provider_subject",
            columnNames = { "provider", "provider_subject" }
        ),
        @UniqueConstraint(
            name = "uq_user_auth_identities_user_provider",
            columnNames = { "user_id", "provider" }
        )
    }
)
public class UserAuthIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_subject", nullable = true)
    private String providerSubject;

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    protected UserAuthIdentityEntity() {
    }

    public static UserAuthIdentityEntity create(
            Integer userId,
            String provider,
            String providerSubject,
            String passwordHash,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        UserAuthIdentityEntity identity = new UserAuthIdentityEntity();
        identity.userId = userId;
        identity.provider = provider;
        identity.providerSubject = providerSubject;
        identity.passwordHash = passwordHash;
        identity.createdAt = createdAt;
        identity.updatedAt = updatedAt;
        return identity;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


