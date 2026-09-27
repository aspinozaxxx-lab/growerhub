package ru.growerhub.backend.user.contract;

import java.time.LocalDateTime;

public final class PushokPilot {
    private PushokPilot() {}

    public enum ContactMethod { TELEGRAM, EMAIL, OTHER }

    public record Request(ContactMethod contactMethod, String contact, String equipment,
                          LocalDateTime requestedAt, LocalDateTime contactedAt) {}

    public record Entry(Integer userId, String username, String email, Request request) {}
}
