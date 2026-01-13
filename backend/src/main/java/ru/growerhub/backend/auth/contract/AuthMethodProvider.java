package ru.growerhub.backend.auth.contract;

public record AuthMethodProvider(boolean active, String providerSubject, boolean canDelete) {
}
