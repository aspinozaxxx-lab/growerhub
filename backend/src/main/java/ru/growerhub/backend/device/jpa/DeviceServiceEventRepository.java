package ru.growerhub.backend.device.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceServiceEventRepository extends JpaRepository<DeviceServiceEventEntity, Integer> {
    List<DeviceServiceEventEntity> findAllByDeviceIdInOrderByReceivedAtDesc(List<Integer> deviceIds);

    void deleteAllByDeviceId(Integer deviceId);
}
