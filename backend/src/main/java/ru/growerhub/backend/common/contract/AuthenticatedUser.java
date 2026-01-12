package ru.growerhub.backend.common.contract;

public record AuthenticatedUser(Integer id, String role) {
    public boolean isAdmin() {
        return role != null && "admin".equals(role);
    }
}
