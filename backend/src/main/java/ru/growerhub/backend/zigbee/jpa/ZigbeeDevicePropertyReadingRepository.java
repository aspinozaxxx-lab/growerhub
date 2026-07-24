package ru.growerhub.backend.zigbee.jpa;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZigbeeDevicePropertyReadingRepository extends JpaRepository<ZigbeeDevicePropertyReadingEntity, Integer> {
    @Query(
            value = """
                    SELECT id, state_event_id, device_snapshot_id, coordinator_id, ieee_address,
                           friendly_name, property, ts, value_numeric, value_text, value_boolean, created_at
                    FROM zigbee_device_property_readings
                    WHERE coordinator_id = :coordinatorId
                      AND ieee_address = :ieeeAddress
                      AND property = :property
                      AND ts >= :since
                    ORDER BY ts DESC, id DESC
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    ZigbeeDevicePropertyReadingEntity findLatestHistoryPoint(
            @Param("coordinatorId") Integer coordinatorId,
            @Param("ieeeAddress") String ieeeAddress,
            @Param("property") String property,
            @Param("since") LocalDateTime since
    );

    @Query(
            value = """
                    SELECT id, state_event_id, device_snapshot_id, coordinator_id, ieee_address,
                           friendly_name, property, ts, value_numeric, value_text, value_boolean, created_at
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
                            FROM zigbee_device_property_readings reading
                            WHERE reading.coordinator_id = :coordinatorId
                              AND reading.ieee_address = :ieeeAddress
                              AND reading.property = :property
                              AND reading.ts >= :since
                              AND reading.value_numeric IS NOT NULL
                        ) bucketed
                    ) ranked
                    WHERE pick_no = 1
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<ZigbeeDevicePropertyReadingEntity> findBucketedNumericHistory(
            @Param("coordinatorId") Integer coordinatorId,
            @Param("ieeeAddress") String ieeeAddress,
            @Param("property") String property,
            @Param("since") LocalDateTime since,
            @Param("maxPoints") int maxPoints
    );

    @Query(
            value = """
                    SELECT id, state_event_id, device_snapshot_id, coordinator_id, ieee_address,
                           friendly_name, property, ts, value_numeric, value_text, value_boolean, created_at
                    FROM (
                        SELECT reading.*,
                               ROW_NUMBER() OVER (
                                   ORDER BY reading.ts, reading.id
                               ) AS row_no,
                               LAG(reading.value_text) OVER (
                                   ORDER BY reading.ts, reading.id
                               ) AS previous_text,
                               LAG(reading.value_boolean) OVER (
                                   ORDER BY reading.ts, reading.id
                               ) AS previous_boolean
                        FROM zigbee_device_property_readings reading
                        WHERE reading.coordinator_id = :coordinatorId
                          AND reading.ieee_address = :ieeeAddress
                          AND reading.property = :property
                          AND reading.ts >= :since
                          AND reading.value_numeric IS NULL
                    ) transitions
                    WHERE row_no = 1
                       OR value_text IS DISTINCT FROM previous_text
                       OR value_boolean IS DISTINCT FROM previous_boolean
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<ZigbeeDevicePropertyReadingEntity> findDiscreteTransitions(
            @Param("coordinatorId") Integer coordinatorId,
            @Param("ieeeAddress") String ieeeAddress,
            @Param("property") String property,
            @Param("since") LocalDateTime since
    );

    @Query(
            value = """
                    SELECT id, state_event_id, device_snapshot_id, coordinator_id, ieee_address,
                           friendly_name, property, ts, value_numeric, value_text, value_boolean, created_at
                    FROM (
                        SELECT reading.*
                        FROM zigbee_device_property_readings reading
                        WHERE reading.coordinator_id = :coordinatorId
                          AND reading.ieee_address = :ieeeAddress
                          AND reading.property = :property
                          AND reading.ts >= :since
                        ORDER BY reading.ts DESC, reading.id DESC
                        LIMIT :maxPoints
                    ) latest
                    ORDER BY ts
                    """,
            nativeQuery = true
    )
    List<ZigbeeDevicePropertyReadingEntity> findLatestEventHistory(
            @Param("coordinatorId") Integer coordinatorId,
            @Param("ieeeAddress") String ieeeAddress,
            @Param("property") String property,
            @Param("since") LocalDateTime since,
            @Param("maxPoints") int maxPoints
    );

    @Query("SELECT MIN(reading.ts) FROM ZigbeeDevicePropertyReadingEntity reading")
    LocalDateTime findOldestTimestamp();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM zigbee_device_property_readings
                    WHERE ts >= :fromTs
                      AND ts < :toTs
                      AND property IN (:ignoredProperties)
                    """,
            nativeQuery = true
    )
    int deleteIgnoredDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("ignoredProperties") Collection<String> ignoredProperties
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM zigbee_device_property_readings
                    WHERE id IN (
                        SELECT id
                        FROM (
                            SELECT id,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY coordinator_id,
                                                    COALESCE(ieee_address, friendly_name),
                                                    property,
                                                    DATE_TRUNC('hour', ts)
                                       ORDER BY ts DESC, id DESC
                                   ) AS row_no
                            FROM zigbee_device_property_readings
                            WHERE ts >= :fromTs
                              AND ts < :toTs
                              AND value_numeric IS NOT NULL
                        ) ranked
                        WHERE row_no > 1
                    )
                    """,
            nativeQuery = true
    )
    int compactNumericDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM zigbee_device_property_readings
                    WHERE id IN (
                        SELECT id
                        FROM (
                            SELECT id,
                                   value_text,
                                   value_boolean,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY coordinator_id,
                                                    COALESCE(ieee_address, friendly_name),
                                                    property
                                       ORDER BY ts, id
                                   ) AS row_no,
                                   LAG(value_text) OVER (
                                       PARTITION BY coordinator_id,
                                                    COALESCE(ieee_address, friendly_name),
                                                    property
                                       ORDER BY ts, id
                                   ) AS previous_text,
                                   LAG(value_boolean) OVER (
                                       PARTITION BY coordinator_id,
                                                    COALESCE(ieee_address, friendly_name),
                                                    property
                                       ORDER BY ts, id
                                   ) AS previous_boolean
                            FROM zigbee_device_property_readings
                            WHERE ts >= :fromTs
                              AND ts < :toTs
                              AND value_numeric IS NULL
                              AND property NOT IN (:eventProperties)
                        ) transitions
                        WHERE row_no > 1
                          AND value_text IS NOT DISTINCT FROM previous_text
                          AND value_boolean IS NOT DISTINCT FROM previous_boolean
                    )
                    """,
            nativeQuery = true
    )
    int compactDiscreteDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("eventProperties") Collection<String> eventProperties
    );
}
