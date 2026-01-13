package ru.growerhub.backend.sensor.contract;

import java.time.LocalDateTime;

public record SensorBoundPlantView(
        Integer id,
        String name,
        LocalDateTime plantedAt,
        String growthStage,
        Integer ageDays
) {
}

