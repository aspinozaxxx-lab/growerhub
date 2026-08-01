package ru.growerhub.backend.device.jpa;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Integer> {
    Optional<DeviceEntity> findByDeviceId(String deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceEntity device where device.deviceId = :deviceId")
    Optional<DeviceEntity> findByDeviceIdForUpdate(@Param("deviceId") String deviceId);

    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Integer lockUserForDeviceClaim(@Param("userId") Integer userId);

    List<DeviceEntity> findAllByUserId(Integer userId);
}
