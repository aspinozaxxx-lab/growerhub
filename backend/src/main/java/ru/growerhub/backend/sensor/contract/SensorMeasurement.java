package ru.growerhub.backend.sensor.contract;

public record SensorMeasurement(SensorType type, int channel, Double value, boolean detected) {
}

