package ru.growerhub.backend.device.contract;

public record DeviceStatusUpdate(
        Boolean lightOn,
        Boolean watering,
        Double soilMoisture,
        Double airTemperature,
        Double airHumidity
) {
}
