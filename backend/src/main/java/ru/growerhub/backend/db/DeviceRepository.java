package ru.growerhub.backend.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Integer> {
    Optional<DeviceEntity> findByDeviceId(String deviceId);

    List<DeviceEntity> findAllByUser_Id(Integer userId);
}
