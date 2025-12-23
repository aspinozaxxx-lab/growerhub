package ru.growerhub.backend.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceStateLastRepository extends JpaRepository<DeviceStateLastEntity, Integer> {
    Optional<DeviceStateLastEntity> findByDeviceId(String deviceId);
}
