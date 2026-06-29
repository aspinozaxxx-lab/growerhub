package ru.growerhub.backend.automation.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRoomRepository extends JpaRepository<AutomationRoomEntity, Integer> {
    List<AutomationRoomEntity> findAllByOrderByNameAscIdAsc();
}
