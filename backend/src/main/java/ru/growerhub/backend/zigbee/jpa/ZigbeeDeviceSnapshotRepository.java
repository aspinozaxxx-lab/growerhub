package ru.growerhub.backend.zigbee.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeDeviceSnapshotRepository extends JpaRepository<ZigbeeDeviceSnapshotEntity, Integer> {
    Optional<ZigbeeDeviceSnapshotEntity> findByIeeeAddress(String ieeeAddress);

    Optional<ZigbeeDeviceSnapshotEntity> findFirstByFriendlyNameOrderByIdAsc(String friendlyName);

    List<ZigbeeDeviceSnapshotEntity> findByCoordinatorTrueOrderByIdAsc();

    List<ZigbeeDeviceSnapshotEntity> findAllByOrderByCoordinatorDescFriendlyNameAsc();
}
