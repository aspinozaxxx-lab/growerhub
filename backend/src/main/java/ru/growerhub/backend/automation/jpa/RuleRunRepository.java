package ru.growerhub.backend.automation.jpa;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleRunRepository extends JpaRepository<RuleRunEntity, UUID> {
    List<RuleRunEntity> findByRuleIdOrderByCreatedAtDesc(UUID ruleId, Pageable page);
    List<RuleRunEntity> findByUserIdAndStatusIn(Integer userId, Collection<String> statuses);
    List<RuleRunEntity> findByUserIdAndCreatedAtAfter(Integer userId, LocalDateTime since);
    boolean existsByRuleIdAndTriggerKey(UUID ruleId, String triggerKey);
    List<RuleRunEntity> findByStatusOrderByNextAtAsc(String status, Pageable page);
}
