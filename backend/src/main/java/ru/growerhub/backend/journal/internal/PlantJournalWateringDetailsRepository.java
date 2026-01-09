package ru.growerhub.backend.journal.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.journal.PlantJournalWateringDetailsEntity;

public interface PlantJournalWateringDetailsRepository
        extends JpaRepository<PlantJournalWateringDetailsEntity, Integer> {
    Optional<PlantJournalWateringDetailsEntity> findByJournalEntry_Id(Integer journalEntryId);
}
