package ru.growerhub.backend.common.config.pump;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pump.water-meter")
public record WaterMeterSettings(int maxHistoryEvents, int lateReportHours, int availableMonths, int clockToleranceSeconds) {}
