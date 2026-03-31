package ru.growerhub.backend.sensor.contract;

import java.time.LocalDateTime;

public record SensorMeasurement(
        SensorType type,
        int channel,
        Double value,
        boolean detected,
        SensorStatus status,
        LocalDateTime statusObservedAt
) {
}
