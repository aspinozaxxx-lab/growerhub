package ru.growerhub.backend.pump.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpRepository extends JpaRepository<PumpEntity, Integer> {
    Optional<PumpEntity> findByDeviceIdAndChannel(Integer deviceId, Integer channel);

    List<PumpEntity> findAllByDeviceId(Integer deviceId);

    void deleteAllByDeviceId(Integer deviceId);
}


