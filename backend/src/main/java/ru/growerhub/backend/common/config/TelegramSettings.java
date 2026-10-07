package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramSettings(boolean enabled, String botUsername, String botToken, String webhookSecret,
        String apiUrl, String proxyHost, int proxyPort, String siteUrl, int linkMinutes, int batchSize, int leaseSeconds, int timeoutSeconds,
        int maxAttempts, int retrySeconds, int retentionDays, int deliveryHours, int digestSize, int defaultHour, int quietFrom, int quietUntil) { }
