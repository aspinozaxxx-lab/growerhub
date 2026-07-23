package ru.growerhub.backend.zigbee.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeDeviceSnapshotRepository extends JpaRepository<ZigbeeDeviceSnapshotEntity, Integer> {
    Optional<ZigbeeDeviceSnapshotEntity> findByIeeeAddress(String ieeeAddress);

    Optional<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndIeeeAddress(Integer coordinatorId, String ieeeAddress);

    Optional<ZigbeeDeviceSnapshotEntity> findFirstByFriendlyNameOrderByIdAsc(String friendlyName);

    Optional<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndFriendlyName(Integer coordinatorId, String friendlyName);

    List<ZigbeeDeviceSnapshotEntity> findByCoordinatorTrueOrderByIdAsc();

    List<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndCoordinatorTrueOrderByIdAsc(Integer coordinatorId);

    List<ZigbeeDeviceSnapshotEntity> findAllByOrderByCoordinatorDescFriendlyNameAsc();

    List<ZigbeeDeviceSnapshotEntity> findAllByCoordinatorIdOrderByCoordinatorDescFriendlyNameAsc(Integer coordinatorId);

    long countByCoordinatorIdAndCoordinatorFalse(Integer coordinatorId);
}
