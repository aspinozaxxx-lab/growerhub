package ru.growerhub.backend.pump.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpStateReadingRepository extends JpaRepository<PumpStateReadingEntity, Integer> {
    List<PumpStateReadingEntity> findAllByPump_IdAndTsGreaterThanEqualOrderByTs(Integer pumpId, LocalDateTime ts);
}
