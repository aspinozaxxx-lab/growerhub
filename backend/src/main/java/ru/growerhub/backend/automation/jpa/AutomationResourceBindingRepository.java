package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationResourceBindingRepository extends JpaRepository<AutomationResourceBindingEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from AutomationResourceBindingEntity binding where binding.id = :id")
    Optional<AutomationResourceBindingEntity> lockById(@Param("id") Integer id);

    List<AutomationResourceBindingEntity> findAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);

    List<AutomationResourceBindingEntity> findAllByScopeTypeAndScopeIdIn(String scopeType, List<Integer> scopeIds);

    Optional<AutomationResourceBindingEntity> findByScopeTypeAndScopeIdAndRole(
            String scopeType,
            Integer scopeId,
            String role
    );

    List<AutomationResourceBindingEntity> findAllByScopeTypeAndScopeIdInOrderByIdAsc(
            String scopeType,
            List<Integer> scopeIds
    );

    void deleteAllByScopeTypeAndScopeId(String scopeType, Integer scopeId);
}
