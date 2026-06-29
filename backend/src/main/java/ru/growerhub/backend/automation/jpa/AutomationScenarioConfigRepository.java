package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationScenarioConfigRepository extends JpaRepository<AutomationScenarioConfigEntity, Integer> {
    List<AutomationScenarioConfigEntity> findAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);

    List<AutomationScenarioConfigEntity> findAllByScopeTypeAndScopeIdIn(String scopeType, List<Integer> scopeIds);

    Optional<AutomationScenarioConfigEntity> findByScopeTypeAndScopeIdAndScenarioType(
            String scopeType,
            Integer scopeId,
            String scenarioType
    );

    void deleteAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);
}
