package ru.growerhub.backend.automation.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlDemandRepository extends JpaRepository<ControlDemandEntity, UUID> {
    List<ControlDemandEntity> findByUserIdAndStatusIn(Integer userId, Collection<String> statuses);
    List<ControlDemandEntity> findByUserIdAndTargetKeyAndStatusIn(Integer userId, String targetKey, Collection<String> statuses);
    Optional<ControlDemandEntity> findByUserIdAndSourceKey(Integer userId, String key);
    List<ControlDemandEntity> findByStatusIn(Collection<String> statuses);
    interface CleanupCandidate { UUID getId(); Integer getUserId(); }
    @org.springframework.data.jpa.repository.Query("select d.id as id, d.userId as userId from ControlDemandEntity d where d.status not in :statuses and d.requestedAt < :before order by d.requestedAt asc")
    List<CleanupCandidate> cleanupCandidates(@org.springframework.data.repository.query.Param("statuses") Collection<String> statuses,
            @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before, org.springframework.data.domain.Pageable page);
    void deleteByUserId(Integer userId);
}
