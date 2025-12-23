package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import ru.growerhub.backend.user.UserEntity;

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

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

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
            UserEntity user,
            String provider,
            String providerSubject,
            String passwordHash,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        UserAuthIdentityEntity identity = new UserAuthIdentityEntity();
        identity.user = user;
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

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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
