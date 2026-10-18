package ru.growerhub.backend.advisor.jpa;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantRequestRepository extends JpaRepository<AssistantRequestEntity, UUID> {
    List<AssistantRequestEntity> findAllByOrderByCreatedAtDesc(Pageable page);
    List<AssistantRequestEntity> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable page);
    List<AssistantRequestEntity> findByNotificationQueuedFalseOrderByCreatedAtAsc(Pageable page);
    long countByUserIdAndCreatedAtAfter(Integer userId, LocalDateTime after);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AssistantRequestEntity r where r.id = :id")
    Optional<AssistantRequestEntity> lock(@Param("id") UUID id);
}
