package ru.growerhub.backend.pump.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PumpStateReadingRepository extends JpaRepository<PumpStateReadingEntity, Integer> {
    Optional<PumpStateReadingEntity> findTopByPump_IdOrderByTsDesc(Integer pumpId);

    Optional<PumpStateReadingEntity> findTopByPump_IdAndTsLessThanOrderByTsDesc(
            Integer pumpId,
            LocalDateTime ts
    );

    @Query(
            value = """
                    SELECT id, pump_id, ts, is_running, raw_status, raw_state_json, created_at
                    FROM (
                        SELECT reading.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY pump_id
                                   ORDER BY ts, id
                               ) AS row_no,
                               LAG(is_running) OVER (
                                   PARTITION BY pump_id
                                   ORDER BY ts, id
                               ) AS previous_running,
                               LAG(raw_status) OVER (
                                   PARTITION BY pump_id
                                   ORDER BY ts, id
                               ) AS previous_status
                        FROM pump_state_readings reading
                        WHERE pump_id = :pumpId
                          AND ts >= :since
                    ) transitions
                    WHERE row_no = 1
                       OR is_running IS DISTINCT FROM previous_running
                       OR raw_status IS DISTINCT FROM previous_status
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<PumpStateReadingEntity> findStateTransitions(
            @Param("pumpId") Integer pumpId,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT MIN(reading.ts) FROM PumpStateReadingEntity reading")
    LocalDateTime findOldestTimestamp();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM pump_state_readings
                    WHERE id IN (
                        SELECT id
                        FROM (
                            SELECT id,
                                   is_running,
                                   raw_status,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY pump_id
                                       ORDER BY ts, id
                                   ) AS row_no,
                                   LAG(is_running) OVER (
                                       PARTITION BY pump_id
                                       ORDER BY ts, id
                                   ) AS previous_running,
                                   LAG(raw_status) OVER (
                                       PARTITION BY pump_id
                                       ORDER BY ts, id
                                   ) AS previous_status
                            FROM pump_state_readings
                            WHERE ts >= :fromTs
                              AND ts < :toTs
                        ) transitions
                        WHERE row_no > 1
                          AND is_running IS NOT DISTINCT FROM previous_running
                          AND raw_status IS NOT DISTINCT FROM previous_status
                    )
                    """,
            nativeQuery = true
    )
    int compactDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
