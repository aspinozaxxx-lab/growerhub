package ru.growerhub.backend.automation.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ControlDemandRepository extends JpaRepository<ControlDemandEntity, UUID> {
    List<ControlDemandEntity> findByUserIdAndStatusIn(Integer userId, Collection<String> statuses);
    List<ControlDemandEntity> findByUserIdAndTargetKeyAndStatusIn(Integer userId, String targetKey, Collection<String> statuses);
    Optional<ControlDemandEntity> findByUserIdAndSourceKey(Integer userId, String key);
    @Query("select distinct d.userId from ControlDemandEntity d where d.status in :statuses or (d.kind='baseline' and d.status='error')")
    List<Integer> currentOwners(@Param("statuses") Collection<String> statuses);
    @Query("select d from ControlDemandEntity d where d.userId=:owner and (d.status in :statuses or (d.kind='baseline' and d.status='error'))")
    List<ControlDemandEntity> currentForOwner(@Param("owner") Integer owner, @Param("statuses") Collection<String> statuses);
    @Query("select d from ControlDemandEntity d where d.userId=:owner and d.targetKey=:target and (d.status in :statuses or (d.kind='baseline' and d.status='error'))")
    List<ControlDemandEntity> currentForTarget(@Param("owner") Integer owner, @Param("target") String target, @Param("statuses") Collection<String> statuses);
    interface CleanupCandidate { UUID getId(); Integer getUserId(); }
    @org.springframework.data.jpa.repository.Query("select d.id as id, d.userId as userId from ControlDemandEntity d where d.status not in :statuses and d.requestedAt < :before order by d.requestedAt asc")
    List<CleanupCandidate> cleanupCandidates(@org.springframework.data.repository.query.Param("statuses") Collection<String> statuses,
            @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before, org.springframework.data.domain.Pageable page);
    void deleteByUserId(Integer userId);
}
