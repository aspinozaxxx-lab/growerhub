package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;

public record SensorReadingSummary(Integer sensorId, SensorType type, LocalDateTime ts, Double value) {
}
