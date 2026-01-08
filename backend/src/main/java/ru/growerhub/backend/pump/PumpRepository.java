package ru.growerhub.backend.pump;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpRepository extends JpaRepository<PumpEntity, Integer> {
    Optional<PumpEntity> findByDevice_IdAndChannel(Integer deviceId, Integer channel);

    List<PumpEntity> findAllByDevice_Id(Integer deviceId);
}
