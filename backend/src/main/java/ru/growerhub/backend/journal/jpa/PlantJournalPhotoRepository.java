package ru.growerhub.backend.journal.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;

public interface PlantJournalPhotoRepository extends JpaRepository<PlantJournalPhotoEntity, Integer> {
    List<PlantJournalPhotoEntity> findAllByJournalEntry_Id(Integer journalEntryId);

    java.util.Optional<PlantJournalPhotoEntity> findByJournalEntry_IdAndContentHash(Integer entryId, String hash);
    long countByJournalEntry_Id(Integer id);
    @org.springframework.data.jpa.repository.Query("select count(p) from PlantJournalPhotoEntity p where p.journalEntry.userId = :owner and p.data is not null")
    long countOwned(@org.springframework.data.repository.query.Param("owner") Integer owner);
    void deleteAllByJournalEntry_Id(Integer id);

    @org.springframework.data.jpa.repository.Query("""
        select new ru.growerhub.backend.journal.contract.JournalPhoto(p.id, p.url, p.caption,
            case when p.data is not null then true else false end)
        from PlantJournalPhotoEntity p where p.journalEntry.id = :entryId order by p.id
        """)
    List<ru.growerhub.backend.journal.contract.JournalPhoto> metadata(@org.springframework.data.repository.query.Param("entryId") Integer entryId);

    @org.springframework.data.jpa.repository.Query("""
        select p.journalEntry.plantId, p.id from PlantJournalPhotoEntity p
        where p.journalEntry.userId = :owner and p.data is not null order by p.id desc
        """)
    List<Object[]> findCovers(@org.springframework.data.repository.query.Param("owner") Integer owner);
}
