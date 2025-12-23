package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserCreateRequest(
            @NotNull
            @JsonProperty("email") String email,
            @JsonProperty("username") String username,
            @JsonProperty("role") String role,
            @NotNull
            @JsonProperty("password") String password
    ) {
    }

    public record UserUpdateRequest(
            @JsonProperty("username") String username,
            @JsonProperty("role") String role,
            @JsonProperty("is_active") Boolean isActive
    ) {
    }
}
