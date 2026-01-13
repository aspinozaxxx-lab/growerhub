package ru.growerhub.backend.sensor.contract;

import java.time.LocalDateTime;

public record SensorHistoryPoint(LocalDateTime ts, Double value) {
}

