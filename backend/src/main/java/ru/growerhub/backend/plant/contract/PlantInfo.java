package ru.growerhub.backend.plant.contract;

import java.time.LocalDateTime;

public record PlantInfo(
        Integer id,
        String name,
        LocalDateTime plantedAt,
        LocalDateTime harvestedAt,
        String plantType,
        String strain,
        String growthStage,
        Integer userId,
        PlantGroupInfo plantGroup
) {
}
