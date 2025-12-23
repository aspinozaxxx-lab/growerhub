package ru.growerhub.backend.db;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorDataRepository extends JpaRepository<SensorDataEntity, Integer> {
    void deleteAllByDeviceId(String deviceId);

    List<SensorDataEntity> findAllByDeviceIdAndTimestampGreaterThanEqualOrderByTimestamp(
            String deviceId,
            LocalDateTime timestamp
    );
}
