package ru.growerhub.backend.device.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.device.DeviceStateLastEntity;

public interface DeviceStateLastRepository extends JpaRepository<DeviceStateLastEntity, Integer> {
    Optional<DeviceStateLastEntity> findByDeviceId(String deviceId);

    void deleteByDeviceId(String deviceId);
}
