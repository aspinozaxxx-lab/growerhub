package ru.growerhub.backend.notification.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface TelegramDeliveryRepository extends JpaRepository<TelegramDeliveryEntity, Long> {
    boolean existsByDedupeKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TelegramDeliveryEntity d where d.status = 'queued' and d.availableAt <= :now order by d.id")
    List<TelegramDeliveryEntity> ready(@Param("now") LocalDateTime now, Pageable page);
    @Modifying
    @Query("update TelegramDeliveryEntity d set d.status = 'cancelled' where d.userId = :user and d.status in ('queued', 'sending')")
    void cancel(@Param("user") Integer user);
    @Modifying
    @Query("update TelegramDeliveryEntity d set d.status = 'uncertain' where d.status = 'sending' and d.leasedUntil < :now")
    void expireLeases(@Param("now") LocalDateTime now);
    @Modifying
    @Query("delete from TelegramDeliveryEntity d where d.expiresAt < :before and d.status <> 'sending'")
    void purge(@Param("before") LocalDateTime before);
}
