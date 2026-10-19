package ru.growerhub.backend.common.config.plant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plant")
public record PlantSettings(String avatarCatalogPath) { }
