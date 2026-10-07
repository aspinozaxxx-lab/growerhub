package ru.growerhub.backend.shop.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShopNotificationRepository extends JpaRepository<ShopNotificationEntity, Long> {
    Optional<ShopNotificationEntity> findByRequestId(Long requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ShopNotificationEntity n where n.id = :id")
    Optional<ShopNotificationEntity> lock(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ShopNotificationEntity n where n.requestId = :id")
    Optional<ShopNotificationEntity> lockByRequestId(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ShopNotificationEntity n where n.status = 'queued' and n.availableAt <= :now order by n.id")
    List<ShopNotificationEntity> ready(@Param("now") LocalDateTime now, Pageable page);
    @Modifying
    @Query("update ShopNotificationEntity n set n.status = 'uncertain', n.lastError = 'LEASE_EXPIRED', n.updatedAt = :now where n.status = 'sending' and n.leasedUntil < :now")
    void expireLeases(@Param("now") LocalDateTime now);
}
