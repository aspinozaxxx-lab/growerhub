package ru.growerhub.backend.automation.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationBoxRepository extends JpaRepository<AutomationBoxEntity, Integer> {
    List<AutomationBoxEntity> findAllByOrderByNameAscIdAsc();

    List<AutomationBoxEntity> findAllByRoom_IdOrderByNameAscIdAsc(Integer roomId);
}
