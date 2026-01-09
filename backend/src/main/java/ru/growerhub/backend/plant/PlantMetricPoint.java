package ru.growerhub.backend.plant;

import java.time.LocalDateTime;

public record PlantMetricPoint(String metricType, LocalDateTime ts, Double value) {
}
