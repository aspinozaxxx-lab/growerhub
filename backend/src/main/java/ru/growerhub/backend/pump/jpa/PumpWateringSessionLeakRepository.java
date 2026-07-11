package ru.growerhub.backend.pump.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpWateringSessionLeakRepository extends JpaRepository<PumpWateringSessionLeakEntity, Long> {
    List<PumpWateringSessionLeakEntity> findAllBySession_IdOrderById(Long sessionId);

    List<PumpWateringSessionLeakEntity> findAllBySessionBox_IdOrderById(Long sessionBoxId);
}
