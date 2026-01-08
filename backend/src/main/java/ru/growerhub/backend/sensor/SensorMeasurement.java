package ru.growerhub.backend.sensor;

public record SensorMeasurement(SensorType type, int channel, Double value, boolean detected) {
}
