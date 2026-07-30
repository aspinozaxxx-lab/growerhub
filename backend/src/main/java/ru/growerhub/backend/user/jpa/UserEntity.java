package ru.growerhub.backend.user.jpa;

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

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

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
        // Translitem: sovmestimost' testovyh fixture; production peredaet timezone iz konfiguracii.
        return create(email, username, role, isActive, "Europe/Moscow", createdAt, updatedAt);
    }

    public static UserEntity create(
            String email,
            String username,
            String role,
            boolean isActive,
            String timezone,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        UserEntity user = new UserEntity();
        user.email = email;
        user.username = username;
        user.role = role;
        user.isActive = isActive;
        user.timezone = timezone;
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

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public LocalDateTime getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public void setOnboardingCompletedAt(LocalDateTime onboardingCompletedAt) {
        this.onboardingCompletedAt = onboardingCompletedAt;
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




