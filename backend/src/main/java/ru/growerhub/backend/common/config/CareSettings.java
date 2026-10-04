package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "care")
public record CareSettings(int pageSize, int maxTextLength, int maxPhotos, int maxUploadBytes,
        long maxImagePixels, int imageEdge, int maxReminders, int maxRepeatDays, int maxAccountPhotos) {}
