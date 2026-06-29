package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationResourceBindingRepository extends JpaRepository<AutomationResourceBindingEntity, Integer> {
    List<AutomationResourceBindingEntity> findAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);

    List<AutomationResourceBindingEntity> findAllByScopeTypeAndScopeIdIn(String scopeType, List<Integer> scopeIds);

    Optional<AutomationResourceBindingEntity> findByScopeTypeAndScopeIdAndRole(
            String scopeType,
            Integer scopeId,
            String role
    );

    void deleteAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);
}
