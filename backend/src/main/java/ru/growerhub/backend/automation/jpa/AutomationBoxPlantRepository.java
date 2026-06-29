package ru.growerhub.backend.automation.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationBoxPlantRepository extends JpaRepository<AutomationBoxPlantEntity, Integer> {
    List<AutomationBoxPlantEntity> findAllByBox_Id(Integer boxId);

    List<AutomationBoxPlantEntity> findAllByBox_IdIn(List<Integer> boxIds);

    void deleteAllByBox_Id(Integer boxId);
}
