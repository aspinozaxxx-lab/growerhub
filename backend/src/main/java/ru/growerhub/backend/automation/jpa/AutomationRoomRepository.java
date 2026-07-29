package ru.growerhub.backend.automation.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRoomRepository extends JpaRepository<AutomationRoomEntity, Integer> {
    List<AutomationRoomEntity> findAllByOrderByNameAscIdAsc();

    List<AutomationRoomEntity> findAllByUserIdOrderByNameAscIdAsc(Integer userId);

    Optional<AutomationRoomEntity> findByIdAndUserId(Integer id, Integer userId);
}
