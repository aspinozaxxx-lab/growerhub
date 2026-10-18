package ru.growerhub.backend.automation.jpa;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleVersionRepository extends JpaRepository<RuleVersionEntity, UUID> {
    List<RuleVersionEntity> findByRuleIdOrderByRevisionDesc(UUID ruleId);
    Optional<RuleVersionEntity> findByRuleIdAndRevision(UUID ruleId, int revision);
}
