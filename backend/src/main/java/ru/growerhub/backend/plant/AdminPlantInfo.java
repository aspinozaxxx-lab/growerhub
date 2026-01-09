package ru.growerhub.backend.plant;

public record AdminPlantInfo(
        Integer id,
        String name,
        String ownerEmail,
        String ownerUsername,
        Integer ownerId,
        String groupName
) {
}
