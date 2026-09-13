package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public record DemoSettings(
        boolean enabled,
        int accessTokenMinutes,
        int guestTtlHours,
        int savedSessionDays,
        int inactiveMinutes,
        int maxGuestSpaces,
        int maxActiveSpaces,
        int maxCreatesPerHour,
        int maxActionsPerMinute,
        int maxResetsPerHour,
        int maxDevices,
        int maxPlants,
        int maxFarms,
        int maxGreenhouses,
        int workerBatchSize,
        int historyDays,
        int historyStepMinutes,
        int telemetryPeriodSeconds,
        String cookieName,
        boolean secureCookie,
        String allowedOrigin,
        java.util.List<String> trustedProxyAddresses,
        String templatePath
) {
}
