package ru.growerhub.backend.device;

public record DeviceStatusUpdate(
        Boolean lightOn,
        Boolean watering,
        Double soilMoisture,
        Double airTemperature,
        Double airHumidity
) {
}
