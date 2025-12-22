package ru.growerhub.backend.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record UserResponse(
        @JsonProperty("id") int id,
        @JsonProperty("email") String email,
        @JsonProperty("username") String username,
        @JsonProperty("role") String role,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {}
