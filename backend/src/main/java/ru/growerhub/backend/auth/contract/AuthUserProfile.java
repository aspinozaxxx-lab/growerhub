package ru.growerhub.backend.auth.contract;

import java.time.LocalDateTime;

public record AuthUserProfile(
        Integer id,
        String email,
        String username,
        String role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
