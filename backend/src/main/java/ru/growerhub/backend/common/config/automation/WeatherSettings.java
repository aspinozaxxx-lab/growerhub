package ru.growerhub.backend.common.config.automation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public record WeatherSettings(String url, String userAgent, int timeoutSeconds, int maximumBodyBytes,
        int maximumLocations, int minimumRequestMillis, int retrySeconds, int maximumForecastAgeHours,
        int horizonHours, double defaultRainMm, int defaultDelayHours, int maximumDelayHours,
        int coordinateDecimals, int demoHorizonHours, double demoRainMm, boolean defaultEnabled,
        String defaultExposure, String defaultUnavailablePolicy, String demoDefaultKind) {}
