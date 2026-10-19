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
        String description,
        String locationLabel,
        String avatarKey,
        String avatarStage
) {
    public PlantInfo(Integer id, String name, LocalDateTime plantedAt, LocalDateTime harvestedAt,
            String plantType, String strain, String growthStage, Integer userId, String description, String locationLabel) {
        this(id, name, plantedAt, harvestedAt, plantType, strain, growthStage, userId, description, locationLabel, null, null);
    }
    public PlantInfo(Integer id, String name, LocalDateTime plantedAt, LocalDateTime harvestedAt,
            String plantType, String strain, String growthStage, Integer userId) {
        this(id, name, plantedAt, harvestedAt, plantType, strain, growthStage, userId, null, null);
    }
}
