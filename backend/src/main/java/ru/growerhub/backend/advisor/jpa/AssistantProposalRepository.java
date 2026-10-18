package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantProposalRepository extends JpaRepository<AssistantProposalEntity, UUID> {
    List<AssistantProposalEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<AssistantProposalEntity> findByTelegramQueuedFalseAndStatusIn(List<String> statuses, org.springframework.data.domain.Pageable page);
    List<AssistantProposalEntity> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    Optional<AssistantProposalEntity> findByJobIdAndToolCallId(UUID jobId, String toolCallId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AssistantProposalEntity p where p.id = :id")
    Optional<AssistantProposalEntity> lock(@Param("id") UUID id);
}
