package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
public interface AssistantJobRepository extends JpaRepository<AssistantJobEntity, UUID> {
    Optional<AssistantJobEntity> findByUserIdAndClientKey(Integer userId, UUID key);
    List<AssistantJobEntity> findByConversationIdOrderByCreatedAtDesc(UUID id, Pageable page);
    List<AssistantJobEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable page);
    List<AssistantJobEntity> findByStatusIn(Collection<String> statuses);
    List<AssistantJobEntity> findByTelegramQueuedFalseAndChannelInAndStatusNotIn(Collection<String> channels, Collection<String> statuses, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AssistantJobEntity j where j.id = :id")
    Optional<AssistantJobEntity> lock(@Param("id") UUID id);
    @Modifying @Query("delete from AssistantJobEntity j where j.createdAt < :before and j.status not in ('queued','running')")
    void purge(@Param("before") LocalDateTime before);
}
