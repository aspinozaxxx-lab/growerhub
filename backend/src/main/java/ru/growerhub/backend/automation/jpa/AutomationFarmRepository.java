package ru.growerhub.backend.automation.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationFarmRepository extends JpaRepository<AutomationFarmEntity, Integer> {
    Optional<AutomationFarmEntity> findByUserId(Integer userId);
}
