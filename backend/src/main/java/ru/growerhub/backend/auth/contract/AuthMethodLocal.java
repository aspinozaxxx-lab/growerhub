package ru.growerhub.backend.auth.contract;

public record AuthMethodLocal(boolean active, String email, boolean canDelete) {
}
