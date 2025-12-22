package ru.growerhub.backend.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantJournalWateringDetailsRepository
        extends JpaRepository<PlantJournalWateringDetailsEntity, Integer> {}
