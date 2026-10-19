package ru.growerhub.backend.common.config.automation;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "automation.rules")
public record RuleSettings(int maximum, int actions, int conditionDepth, int conditionNodes, int minIntervalSeconds,
        int maxDelaySeconds, int maxWateringSeconds, int maxDailyWateringSeconds, int maxFreshnessSeconds,
        int minCooldownSeconds, int workerMs, int leaseSeconds, int historyLimit,
        int maxIntervalSeconds, int maxTotalWateringSeconds, int scheduleGraceSeconds,
        int previewRuns, int previewTimelineSteps) {}
