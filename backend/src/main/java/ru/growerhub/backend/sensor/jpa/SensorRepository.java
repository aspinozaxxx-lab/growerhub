package ru.growerhub.backend.sensor.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.sensor.SensorType;

public interface SensorRepository extends JpaRepository<SensorEntity, Integer> {
    Optional<SensorEntity> findByDeviceIdAndTypeAndChannel(Integer deviceId, SensorType type, Integer channel);

    List<SensorEntity> findAllByDeviceId(Integer deviceId);

    void deleteAllByDeviceId(Integer deviceId);
}


