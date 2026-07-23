package ru.growerhub.backend.zigbee.jpa;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZigbeeDeviceStateEventRepository extends JpaRepository<ZigbeeDeviceStateEventEntity, Integer> {
    @Query("SELECT MIN(event.ts) FROM ZigbeeDeviceStateEventEntity event")
    LocalDateTime findOldestTimestamp();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    DELETE FROM zigbee_device_state_events
                    WHERE ts >= :fromTs
                      AND ts < :toTs
                      AND NOT EXISTS (
                          SELECT 1
                          FROM zigbee_device_property_readings reading
                          WHERE reading.state_event_id = zigbee_device_state_events.id
                      )
                    """,
            nativeQuery = true
    )
    int deleteOrphanedDay(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
