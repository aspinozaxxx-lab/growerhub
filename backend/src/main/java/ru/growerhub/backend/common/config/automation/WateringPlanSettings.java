package ru.growerhub.backend.common.config.automation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "automation.watering-plan")
public record WateringPlanSettings(int executionWindowSeconds, int historyHours, int minimumSamples,
        int minimumSpanHours, int maximumGapMinutes, double minimumAnchorRange, double maximumJumpFraction,
        double minimumDryingPerHour, int soakMinutes, int responseHours, double minimumResponseFraction,
        int defaultMinIntervalHours, int defaultDailyMaxSeconds, String defaultTime, String defaultWindowEnd) {}
