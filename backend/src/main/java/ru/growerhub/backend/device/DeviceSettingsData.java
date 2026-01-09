package ru.growerhub.backend.device;

public record DeviceSettingsData(
        Double targetMoisture,
        Integer wateringDuration,
        Integer wateringTimeout,
        Integer lightOnHour,
        Integer lightOffHour,
        Integer lightDuration,
        Boolean updateAvailable
) {
}
