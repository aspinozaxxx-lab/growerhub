package ru.growerhub.backend.sensor.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Integer> {
    Optional<SensorReadingEntity> findTopBySensor_IdOrderByTsDesc(Integer sensorId);

    List<SensorReadingEntity> findAllBySensor_IdAndTsGreaterThanEqualOrderByTs(Integer sensorId, LocalDateTime ts);
}


