package ru.growerhub.backend.sensor.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorType;

public interface SensorRepository extends JpaRepository<SensorEntity, Integer> {
    Optional<SensorEntity> findByDevice_IdAndTypeAndChannel(Integer deviceId, SensorType type, Integer channel);

    List<SensorEntity> findAllByDevice_Id(Integer deviceId);
}
