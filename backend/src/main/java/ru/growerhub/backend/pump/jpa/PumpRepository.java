package ru.growerhub.backend.pump.jpa;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PumpRepository extends JpaRepository<PumpEntity, Integer> {
    Optional<PumpEntity> findByDeviceIdAndChannel(Integer deviceId, Integer channel);

    List<PumpEntity> findAllByDeviceId(Integer deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pump from PumpEntity pump where pump.deviceId = :deviceId order by pump.id")
    List<PumpEntity> lockAllByDeviceId(@Param("deviceId") Integer deviceId);

    void deleteAllByDeviceId(Integer deviceId);
}


