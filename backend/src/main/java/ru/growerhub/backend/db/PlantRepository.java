package ru.growerhub.backend.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<PlantEntity, Integer> {}
