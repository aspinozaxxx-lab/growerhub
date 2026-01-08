package ru.growerhub.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.journal.PlantJournalEntryEntity;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantGroupEntity;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserRefreshTokenEntity;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email", columnNames = "email")
    }
)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "username", nullable = true)
    private String username;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user")
    private List<DeviceEntity> devices;

    @OneToMany(mappedBy = "user")
    private List<UserAuthIdentityEntity> authIdentities;

    @OneToMany(mappedBy = "user")
    private List<UserRefreshTokenEntity> refreshTokens;

    @OneToMany(mappedBy = "user")
    private List<PlantGroupEntity> plantGroups;

    @OneToMany(mappedBy = "user")
    private List<PlantEntity> plants;

    @OneToMany(mappedBy = "user")
    private List<PlantJournalEntryEntity> plantJournalEntries;

    protected UserEntity() {
    }

    public static UserEntity create(
            String email,
            String username,
            String role,
            boolean isActive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.username = username;
        user.role = role;
        user.isActive = isActive;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        return user;
    }

    public Integer getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
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

    public List<DeviceEntity> getDevices() {
        return devices;
    }

    public List<UserAuthIdentityEntity> getAuthIdentities() {
        return authIdentities;
    }

    public List<UserRefreshTokenEntity> getRefreshTokens() {
        return refreshTokens;
    }

    public List<PlantGroupEntity> getPlantGroups() {
        return plantGroups;
    }

    public List<PlantEntity> getPlants() {
        return plants;
    }

    public List<PlantJournalEntryEntity> getPlantJournalEntries() {
        return plantJournalEntries;
    }
}
