package ru.growerhub.backend.device.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;

public interface DeviceStateLastRepository extends JpaRepository<DeviceStateLastEntity, Integer> {
    Optional<DeviceStateLastEntity> findByDeviceId(String deviceId);

    void deleteByDeviceId(String deviceId);
}
