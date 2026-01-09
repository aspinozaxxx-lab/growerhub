package ru.growerhub.backend.auth;

public record AuthMethodLocal(boolean active, String email, boolean canDelete) {
}
