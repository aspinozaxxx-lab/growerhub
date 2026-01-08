package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;

public record SensorBoundPlantView(
        Integer id,
        String name,
        LocalDateTime plantedAt,
        String growthStage,
        Integer ageDays
) {
}
