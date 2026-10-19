package ru.growerhub.backend.plant.contract;

public record PlantAvatarOption(String id, String nameRu, String nameEn, java.util.List<Stage> stages) {
    public record Stage(String id, String nameRu, String nameEn) { }
}
