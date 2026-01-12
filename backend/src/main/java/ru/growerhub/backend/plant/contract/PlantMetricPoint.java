package ru.growerhub.backend.plant.contract;

import java.time.LocalDateTime;

public record PlantMetricPoint(String metricType, LocalDateTime ts, Double value) {
}
