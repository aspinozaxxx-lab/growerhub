package ru.growerhub.backend.device.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.device.jpa.DeviceEntity;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Integer> {
    Optional<DeviceEntity> findByDeviceId(String deviceId);

    List<DeviceEntity> findAllByUserId(Integer userId);
}
