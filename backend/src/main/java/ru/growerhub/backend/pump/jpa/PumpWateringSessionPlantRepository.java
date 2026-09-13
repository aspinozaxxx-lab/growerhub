package ru.growerhub.backend.pump.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpWateringSessionPlantRepository extends JpaRepository<PumpWateringSessionPlantEntity, Long> {
    void deleteAllBySession_Id(Long sessionId);

    List<PumpWateringSessionPlantEntity> findAllBySession_IdOrderById(Long sessionId);

    List<PumpWateringSessionPlantEntity> findAllBySessionBox_IdOrderById(Long sessionBoxId);
}
