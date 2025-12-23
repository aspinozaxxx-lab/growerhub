package ru.growerhub.backend.db;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
