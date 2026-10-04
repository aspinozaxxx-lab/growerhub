package ru.growerhub.backend.auth.contract;

import java.time.LocalDateTime;

public record AuthUserProfile(
        Integer id,
        String email,
        String username,
        String role,
        boolean active,
        String timezone,
        boolean onboardingCompleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean careStarted
) {
    public AuthUserProfile(Integer id, String email, String username, String role, boolean active, String timezone, boolean onboardingCompleted, LocalDateTime createdAt, LocalDateTime updatedAt) { this(id, email, username, role, active, timezone, onboardingCompleted, createdAt, updatedAt, false); }
}
