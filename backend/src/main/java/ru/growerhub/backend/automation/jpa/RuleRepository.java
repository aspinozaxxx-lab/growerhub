package ru.growerhub.backend.automation.jpa;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {
    List<RuleEntity> findByUserIdOrderByUpdatedAtDesc(Integer userId);
    List<RuleEntity> findByEnabledTrue();
    long countByUserId(Integer userId);
}
