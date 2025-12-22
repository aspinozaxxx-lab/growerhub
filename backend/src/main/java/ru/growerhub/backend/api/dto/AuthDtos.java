package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password
    ) {
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    public record UserProfileUpdateRequest(
            @JsonProperty("email") String email,
            @JsonProperty("username") String username
    ) {
    }

    public record PasswordChangeRequest(
            @JsonProperty("current_password") String currentPassword,
            @JsonProperty("new_password") String newPassword
    ) {
    }

    public record AuthMethodLocalRequest(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password
    ) {
    }

    public record AuthMethodLocalStatus(
            @JsonProperty("active") Boolean active,
            @JsonProperty("email") String email,
            @JsonProperty("can_delete") Boolean canDelete
    ) {
    }

    public record AuthMethodProviderStatus(
            @JsonProperty("linked") Boolean linked,
            @JsonProperty("provider_subject") String providerSubject,
            @JsonProperty("can_delete") Boolean canDelete
    ) {
    }

    public record AuthMethodsResponse(
            @JsonProperty("local") AuthMethodLocalStatus local,
            @JsonProperty("google") AuthMethodProviderStatus google,
            @JsonProperty("yandex") AuthMethodProviderStatus yandex
    ) {
    }

    public record SsoLoginResponse(@JsonProperty("url") String url) {
    }
}
