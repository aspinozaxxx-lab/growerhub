package ru.growerhub.backend.device.contract;

public record DeviceSettingsUpdate(
        Double targetMoisture,
        Integer wateringDuration,
        Integer wateringTimeout,
        Integer lightOnHour,
        Integer lightOffHour,
        Integer lightDuration
) {
}
