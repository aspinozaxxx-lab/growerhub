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

    Optional<PlantJournalEntryEntity> findByUserIdAndClientKey(Integer userId, String clientKey);

    @Query("""
            select e from PlantJournalEntryEntity e where e.userId = :owner
            and (:plantId = -1 or e.plantId = :plantId)
            and (lower(coalesce(e.text, '')) like :pattern escape '!' or e.plantId in :matching)
            and (:action = '' or e.careAction = :action
                or (e.careAction is null and e.wateringDetails is null and (e.type = :action or (:action = 'note' and e.type = 'other')))
                or (:action = 'automatic' and e.wateringDetails is not null))
            and e.eventAt >= :since and e.eventAt < :until
            order by e.eventAt desc, e.id desc
            """)
    org.springframework.data.domain.Slice<PlantJournalEntryEntity> searchCare(
            @Param("owner") Integer owner, @Param("plantId") Integer plantId, @Param("pattern") String pattern,
            @Param("action") String action, @Param("matching") List<Integer> matching,
            @Param("since") LocalDateTime since, @Param("until") LocalDateTime until,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select e.plantId, max(case when e.type = 'watering' then e.eventAt else null end), max(e.eventAt), count(e)
            from PlantJournalEntryEntity e where e.userId = :owner group by e.plantId
            """)
    List<Object[]> careSummaries(@Param("owner") Integer owner);
}
