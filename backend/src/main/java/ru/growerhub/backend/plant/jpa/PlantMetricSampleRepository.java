package ru.growerhub.backend.plant.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantMetricSampleRepository extends JpaRepository<PlantMetricSampleEntity, Integer> {
    @Query(
            value = """
                    SELECT id, plant_id, metric_type, ts, value_numeric, created_at
                    FROM (
                        SELECT bucketed.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY metric_type, bucket_no
                                   ORDER BY ts DESC, id DESC
                               ) AS pick_no
                        FROM (
                            SELECT sample.*,
                                   NTILE(CAST(:maxPoints AS INTEGER)) OVER (
                                       PARTITION BY sample.metric_type
                                       ORDER BY sample.ts, sample.id
                                   ) AS bucket_no
                            FROM plant_metric_samples sample
                            WHERE sample.plant_id = :plantId
                              AND sample.metric_type IN (:metricTypes)
                              AND sample.ts >= :since
                        ) bucketed
                    ) ranked
                    WHERE pick_no = 1
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<PlantMetricSampleEntity> findBucketedHistory(
            @Param("plantId") Integer plantId,
            @Param("metricTypes") List<String> metricTypes,
            @Param("since") LocalDateTime since,
            @Param("maxPoints") int maxPoints
    );

    @Query(
            value = """
                    SELECT id, plant_id, metric_type, ts, value_numeric, created_at
                    FROM (
                        SELECT sample.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY sample.metric_type,
                                                FLOOR(
                                                    EXTRACT(
                                                        EPOCH FROM (
                                                            sample.ts - CAST(:since AS TIMESTAMP)
                                                        )
                                                    ) / CAST(:bucketSeconds AS DOUBLE PRECISION)
                                                )
                                   ORDER BY sample.ts DESC, sample.id DESC
                               ) AS pick_no
                        FROM plant_metric_samples sample
                        WHERE sample.plant_id = :plantId
                          AND sample.metric_type IN (:metricTypes)
                          AND sample.ts >= :since
                    ) ranked
                    WHERE pick_no = 1
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<PlantMetricSampleEntity> findLatestByTimeBuckets(
            @Param("plantId") Integer plantId,
            @Param("metricTypes") List<String> metricTypes,
            @Param("since") LocalDateTime since,
            @Param("bucketSeconds") long bucketSeconds
    );

    @Query("SELECT MIN(sample.ts) FROM PlantMetricSampleEntity sample")
    LocalDateTime findOldestTimestamp();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM plant_metric_samples
                    WHERE id IN (
                        SELECT id
                        FROM (
                            SELECT id,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY plant_id, metric_type, DATE_TRUNC('hour', ts)
                                       ORDER BY ts DESC, id DESC
                                   ) AS row_no
                            FROM plant_metric_samples
                            WHERE ts >= :fromTs
                              AND ts < :toTs
                              AND metric_type <> 'WATERING_VOLUME_L'
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
