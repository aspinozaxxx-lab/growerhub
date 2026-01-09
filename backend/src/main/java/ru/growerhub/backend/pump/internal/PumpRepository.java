package ru.growerhub.backend.pump.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.pump.PumpEntity;

public interface PumpRepository extends JpaRepository<PumpEntity, Integer> {
    Optional<PumpEntity> findByDevice_IdAndChannel(Integer deviceId, Integer channel);

    List<PumpEntity> findAllByDevice_Id(Integer deviceId);
}
