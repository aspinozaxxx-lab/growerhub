package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DemoSpaceRepository extends JpaRepository<DemoSpaceEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DemoSpaceEntity> findByAccountUserId(Integer accountId);
    Optional<DemoSpaceEntity> findByDataUserId(Integer userId);
    long countByAccountUserIdIsNull();
    long countByAdmissionKeyAndCreatedAtAfter(String key, LocalDateTime after);
    long countByLastActiveAtAfter(LocalDateTime after);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DemoSpaceEntity s where s.id = :id")
    Optional<DemoSpaceEntity> lockById(@Param("id") UUID id);
    @Query("select s.id from DemoSpaceEntity s where s.lastActiveAt > :active and (s.expiresAt is null or s.expiresAt > :now) order by s.lastTickAt nulls first")
    List<UUID> findActiveIds(@Param("active") LocalDateTime active, @Param("now") LocalDateTime now, Pageable page);
    @Query("select s.id from DemoSpaceEntity s where s.lastActiveAt <= :active and s.paused = false")
    List<UUID> findIdleIds(@Param("active") LocalDateTime active, Pageable page);
    @Query("select s.id from DemoSpaceEntity s where s.accountUserId is null and s.expiresAt < :now")
    List<UUID> findExpiredIds(@Param("now") LocalDateTime now, Pageable page);
}
