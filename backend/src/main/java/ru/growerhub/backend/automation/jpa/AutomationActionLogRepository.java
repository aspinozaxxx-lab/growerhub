package ru.growerhub.backend.automation.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationActionLogRepository extends JpaRepository<AutomationActionLogEntity, Integer> {
    List<AutomationActionLogEntity> findTop20ByOrderByCreatedAtDesc();

    List<AutomationActionLogEntity> findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(String scopeType, Integer scopeId);

    AutomationActionLogEntity findTopByScopeTypeAndScopeIdAndScenarioTypeAndActionOrderByCreatedAtDesc(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            String action
    );

    @Query("""
            select coalesce(sum(l.durationS), 0)
            from AutomationActionLogEntity l
            where l.scopeType = :scopeType
              and l.scopeId = :scopeId
              and l.scenarioType = :scenarioType
              and l.action = :action
              and l.result = :result
              and l.createdAt >= :since
            """)
    Long sumDurationSince(
            @Param("scopeType") String scopeType,
            @Param("scopeId") Integer scopeId,
            @Param("scenarioType") String scenarioType,
            @Param("action") String action,
            @Param("result") String result,
            @Param("since") LocalDateTime since
    );
}
