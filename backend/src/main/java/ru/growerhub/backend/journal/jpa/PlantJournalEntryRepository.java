package ru.growerhub.backend.journal.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;

public interface PlantJournalEntryRepository extends JpaRepository<PlantJournalEntryEntity, Integer> {

    @Query("""
            select entry, details
            from PlantJournalEntryEntity entry
            join entry.wateringDetails details
            where entry.type = 'watering'
              and entry.plantId in :plantIds
              and entry.eventAt >= :since
            order by entry.eventAt desc
            """)
    List<Object[]> findWateringEntries(
            @Param("plantIds") List<Integer> plantIds,
            @Param("since") LocalDateTime since
    );

    List<PlantJournalEntryEntity> findAllByPlantIdOrderByEventAtDesc(Integer plantId);

    List<PlantJournalEntryEntity> findAllByPlantIdOrderByEventAtAsc(Integer plantId);

    Optional<PlantJournalEntryEntity> findTopByPlantIdAndTypeOrderByEventAtDesc(Integer plantId, String type);

    Optional<PlantJournalEntryEntity> findByIdAndPlantIdAndUserId(Integer id, Integer plantId, Integer userId);

    void deleteAllByPlantId(Integer plantId);
}
