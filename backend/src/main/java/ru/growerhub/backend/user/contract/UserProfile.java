package ru.growerhub.backend.user.contract;

import java.time.LocalDateTime;

public record UserProfile(
        Integer id,
        String email,
        String username,
        String role,
        boolean active,
        String timezone,
        LocalDateTime onboardingCompletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
