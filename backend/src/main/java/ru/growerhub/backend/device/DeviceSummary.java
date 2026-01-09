package ru.growerhub.backend.device;

import java.time.LocalDateTime;

public record DeviceSummary(
        Integer id,
        String deviceId,
        String name,
        Boolean isOnline,
        LocalDateTime lastSeen,
        Double targetMoisture,
        Integer wateringDuration,
        Integer wateringTimeout,
        Integer lightOnHour,
        Integer lightOffHour,
        Integer lightDuration,
        String currentVersion,
        Boolean updateAvailable,
        String firmwareVersion,
        Integer userId
) {
}
