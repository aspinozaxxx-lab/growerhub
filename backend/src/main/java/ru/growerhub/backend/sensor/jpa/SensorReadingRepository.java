package ru.growerhub.backend.sensor.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Integer> {
    Optional<SensorReadingEntity> findTopBySensor_IdOrderByTsDesc(Integer sensorId);

    @Query(
            value = """
                    SELECT id, sensor_id, ts, value_numeric, created_at
                    FROM (
                        SELECT bucketed.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY bucket_no
                                   ORDER BY ts DESC, id DESC
                               ) AS pick_no
                        FROM (
                            SELECT reading.*,
                                   NTILE(CAST(:maxPoints AS INTEGER)) OVER (
                                       ORDER BY reading.ts, reading.id
                                   ) AS bucket_no
                            FROM sensor_readings reading
                            WHERE reading.sensor_id = :sensorId
                              AND reading.ts >= :since
                        ) bucketed
                    ) ranked
                    WHERE pick_no = 1
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<SensorReadingEntity> findBucketedHistory(
            @Param("sensorId") Integer sensorId,
            @Param("since") LocalDateTime since,
            @Param("maxPoints") int maxPoints
    );

    @Query("SELECT MIN(reading.ts) FROM SensorReadingEntity reading")
    LocalDateTime findOldestTimestamp();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM sensor_readings
                    WHERE id IN (
                        SELECT id
                        FROM (
                            SELECT id,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY sensor_id, DATE_TRUNC('hour', ts)
                                       ORDER BY ts DESC, id DESC
                                   ) AS row_no
                            FROM sensor_readings
                            WHERE ts >= :fromTs
                              AND ts < :toTs
                        ) ranked
                        WHERE row_no > 1
                    )
                    """,
            nativeQuery = true
    )
    int compactDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
