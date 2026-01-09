package ru.growerhub.backend.journal.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.journal.PlantJournalPhotoEntity;

public interface PlantJournalPhotoRepository extends JpaRepository<PlantJournalPhotoEntity, Integer> {
    List<PlantJournalPhotoEntity> findAllByJournalEntry_Id(Integer journalEntryId);
}
