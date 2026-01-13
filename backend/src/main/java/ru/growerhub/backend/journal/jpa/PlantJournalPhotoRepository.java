package ru.growerhub.backend.journal.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;

public interface PlantJournalPhotoRepository extends JpaRepository<PlantJournalPhotoEntity, Integer> {
    List<PlantJournalPhotoEntity> findAllByJournalEntry_Id(Integer journalEntryId);
}
