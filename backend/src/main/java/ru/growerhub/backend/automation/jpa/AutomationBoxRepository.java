package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationBoxRepository extends JpaRepository<AutomationBoxEntity, Integer> {
    List<AutomationBoxEntity> findAllByOrderByNameAscIdAsc();

    List<AutomationBoxEntity> findAllByRoom_IdOrderByNameAscIdAsc(Integer roomId);

    List<AutomationBoxEntity> findAllByRoom_UserIdOrderByNameAscIdAsc(Integer userId);

    Optional<AutomationBoxEntity> findByIdAndRoom_UserId(Integer id, Integer userId);
}
