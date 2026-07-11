package ru.growerhub.backend.pump.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PumpWateringSessionBoxRepository extends JpaRepository<PumpWateringSessionBoxEntity, Long> {
    List<PumpWateringSessionBoxEntity> findAllBySession_IdOrderById(Long sessionId);

    @Query("""
            select box from PumpWateringSessionBoxEntity box
            where box.boxId = :boxId
              and box.session.finishedAt is not null
              and box.session.activeDurationS > 0
            order by box.session.finishedAt desc, box.session.id desc
            """)
    List<PumpWateringSessionBoxEntity> findLastCompletedForBox(
            @Param("boxId") Integer boxId,
            Pageable pageable
    );

    Optional<PumpWateringSessionBoxEntity> findFirstByBoxIdAndSession_ActiveDeviceKeyIsNotNullOrderBySession_IdDesc(
            Integer boxId
    );

    List<PumpWateringSessionBoxEntity> findAllByBoxIdAndSession_FinishedAtGreaterThanEqualAndSession_FinishedAtLessThan(
            Integer boxId,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
            select box from PumpWateringSessionBoxEntity box
            where box.boxId = :boxId
              and box.session.finishedAt >= :from
              and box.session.finishedAt < :to
              and (:beforeId is null or box.session.id < :beforeId)
            order by box.session.id desc
            """)
    List<PumpWateringSessionBoxEntity> findPageByBoxId(
            @Param("boxId") Integer boxId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );
}
