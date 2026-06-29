package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationScenarioStateRepository extends JpaRepository<AutomationScenarioStateEntity, Integer> {
    List<AutomationScenarioStateEntity> findAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);

    List<AutomationScenarioStateEntity> findAllByScopeTypeAndScopeIdIn(String scopeType, List<Integer> scopeIds);

    Optional<AutomationScenarioStateEntity> findByScopeTypeAndScopeIdAndScenarioType(
            String scopeType,
            Integer scopeId,
            String scenarioType
    );

    List<AutomationScenarioStateEntity> findAllByScenarioTypeAndAcRequestActiveTrue(String scenarioType);

    void deleteAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);
}
