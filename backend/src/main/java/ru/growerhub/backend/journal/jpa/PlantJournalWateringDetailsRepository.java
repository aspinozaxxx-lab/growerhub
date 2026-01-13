package ru.growerhub.backend.journal.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;

public interface PlantJournalWateringDetailsRepository
        extends JpaRepository<PlantJournalWateringDetailsEntity, Integer> {
    Optional<PlantJournalWateringDetailsEntity> findByJournalEntry_Id(Integer journalEntryId);
}
