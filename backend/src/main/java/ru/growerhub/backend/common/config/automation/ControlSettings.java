package ru.growerhub.backend.common.config.automation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "automation.controls")
public record ControlSettings(int workerMs, int ackSeconds, int freshnessSeconds, int baselineTtlSeconds,
        int scenarioPriority, int legacyPriority, int manualPriority, int minRulePriority, int maxRulePriority,
        int minTtlSeconds, int maxTtlSeconds, int defaultManualTtlSeconds, int commandsPerTick, int pendingTtlSeconds,
        int retentionDays, int cleanupBatch, long cleanupMs) {
    public ControlSettings {
        if (commandsPerTick < 1) throw new IllegalArgumentException("automation.controls.commands-per-tick must be positive");
        if (minRulePriority < 1 || (long) manualPriority <= 1L + Math.max(maxRulePriority, Math.max(scenarioPriority, legacyPriority)) || scenarioPriority < 1 || legacyPriority < 1)
            throw new IllegalArgumentException("Control priorities must leave baseline below routines and emergency below manual control");
    }
    public int emergencyPriority() { return 1 + Math.max(maxRulePriority, Math.max(scenarioPriority, legacyPriority)); }
}
