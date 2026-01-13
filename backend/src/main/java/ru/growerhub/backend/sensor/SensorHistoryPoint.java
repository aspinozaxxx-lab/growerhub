package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;

public record SensorHistoryPoint(LocalDateTime ts, Double value) {
}

