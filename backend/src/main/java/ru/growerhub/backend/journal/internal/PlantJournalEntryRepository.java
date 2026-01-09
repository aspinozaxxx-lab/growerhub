package ru.growerhub.backend.journal.internal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.growerhub.backend.journal.PlantJournalEntryEntity;

public interface PlantJournalEntryRepository extends JpaRepository<PlantJournalEntryEntity, Integer> {

    @Query("""
            select entry, details
            from PlantJournalEntryEntity entry
            join entry.wateringDetails details
            join entry.plant plant
            where entry.type = 'watering'
              and plant.id in :plantIds
              and entry.eventAt >= :since
            order by entry.eventAt desc
            """)
    List<Object[]> findWateringEntries(
            @Param("plantIds") List<Integer> plantIds,
            @Param("since") LocalDateTime since
    );

    List<PlantJournalEntryEntity> findAllByPlant_IdOrderByEventAtDesc(Integer plantId);

    List<PlantJournalEntryEntity> findAllByPlant_IdOrderByEventAtAsc(Integer plantId);

    Optional<PlantJournalEntryEntity> findByIdAndPlant_IdAndUser_Id(Integer id, Integer plantId, Integer userId);

    void deleteAllByPlant_Id(Integer plantId);
}
