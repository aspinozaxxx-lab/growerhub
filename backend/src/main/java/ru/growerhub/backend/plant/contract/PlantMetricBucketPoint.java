package ru.growerhub.backend.plant.contract;

import java.time.LocalDateTime;

public record PlantMetricBucketPoint(
        String metricType,
        LocalDateTime bucketStartTs,
        Double value
) {
}
