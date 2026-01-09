package ru.growerhub.backend.auth;

public record AuthMethodProvider(boolean active, String providerSubject, boolean canDelete) {
}
