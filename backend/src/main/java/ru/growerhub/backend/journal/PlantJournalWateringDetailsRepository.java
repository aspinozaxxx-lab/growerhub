package ru.growerhub.backend.journal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantJournalWateringDetailsRepository
        extends JpaRepository<PlantJournalWateringDetailsEntity, Integer> {
    Optional<PlantJournalWateringDetailsEntity> findByJournalEntry_Id(Integer journalEntryId);
}
