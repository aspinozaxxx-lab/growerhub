package ru.growerhub.backend.journal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantJournalPhotoRepository extends JpaRepository<PlantJournalPhotoEntity, Integer> {
    List<PlantJournalPhotoEntity> findAllByJournalEntry_Id(Integer journalEntryId);
}
