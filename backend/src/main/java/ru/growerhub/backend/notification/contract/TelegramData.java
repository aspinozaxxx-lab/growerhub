package ru.growerhub.backend.notification.contract;

import java.time.LocalDateTime;

public final class TelegramData {
    private TelegramData() { }
    public record Status(boolean available, boolean connected, String name, String pendingName, String confirmation,
            boolean enabled, int hour, int quietFrom, int quietUntil, String timezone, String lastStatus) { }
    public record Link(String url, LocalDateTime expiresAt) { }
    public record Preferences(boolean enabled, int hour, int quietFrom, int quietUntil) { }
    public record Update(long updateId, long chatId, boolean privateChat, String name, String text,
            String callbackId, String callbackData) { }
    public record Delivery(Long id, long chatId, String text, String keyboard, String leaseKey) { }
    public record Result(String status, Integer retryAfter) { }
}
