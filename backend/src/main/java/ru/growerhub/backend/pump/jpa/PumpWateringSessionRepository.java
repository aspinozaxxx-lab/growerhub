package ru.growerhub.backend.pump.jpa;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PumpWateringSessionRepository extends JpaRepository<PumpWateringSessionEntity, Long> {
    Optional<PumpWateringSessionEntity> findFirstByPumpIdAndActiveDeviceKeyIsNotNullOrderByIdDesc(Integer pumpId);

    List<PumpWateringSessionEntity> findAllByDeviceId(Integer deviceId);

    Optional<PumpWateringSessionEntity> findByActiveDeviceKey(String activeDeviceKey);

    List<PumpWateringSessionEntity> findByZigbeeCoordinatorId(Integer coordinatorId);
    boolean existsByZigbeeCoordinatorIdAndActiveDeviceKeyIsNotNull(Integer coordinatorId);

    @Query("select s from PumpWateringSessionEntity s where s.executorKey = :key and (:beforeId is null or s.id < :beforeId) order by s.id desc")
    List<PumpWateringSessionEntity> findPageByExecutorKey(@Param("key") String key,
            @Param("beforeId") Long beforeId, Pageable pageable);

    List<PumpWateringSessionEntity> findAllByActiveDeviceKeyIsNotNullOrderByIdAsc();

    @Query("""
            select session from PumpWateringSessionEntity session
            where session.pumpId = :pumpId
              and (:beforeId is null or session.id < :beforeId)
            order by session.id desc
            """)
    List<PumpWateringSessionEntity> findPageByPumpId(
            @Param("pumpId") Integer pumpId,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PumpWateringSessionEntity session where session.id = :sessionId")
    Optional<PumpWateringSessionEntity> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
