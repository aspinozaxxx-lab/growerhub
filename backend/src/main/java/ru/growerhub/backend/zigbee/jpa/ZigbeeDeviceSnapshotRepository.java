package ru.growerhub.backend.zigbee.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeDeviceSnapshotRepository extends JpaRepository<ZigbeeDeviceSnapshotEntity, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from ZigbeeDeviceSnapshotEntity d where d.coordinatorId = :coordinatorId and d.ieeeAddress = :ieeeAddress")
    Optional<ZigbeeDeviceSnapshotEntity> lockWateringDevice(
            @org.springframework.data.repository.query.Param("coordinatorId") Integer coordinatorId,
            @org.springframework.data.repository.query.Param("ieeeAddress") String ieeeAddress);
    Optional<ZigbeeDeviceSnapshotEntity> findByIeeeAddress(String ieeeAddress);

    Optional<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndIeeeAddress(Integer coordinatorId, String ieeeAddress);

    Optional<ZigbeeDeviceSnapshotEntity> findFirstByFriendlyNameOrderByIdAsc(String friendlyName);

    Optional<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndFriendlyName(Integer coordinatorId, String friendlyName);

    List<ZigbeeDeviceSnapshotEntity> findByCoordinatorTrueOrderByIdAsc();

    List<ZigbeeDeviceSnapshotEntity> findByCoordinatorIdAndCoordinatorTrueOrderByIdAsc(Integer coordinatorId);

    List<ZigbeeDeviceSnapshotEntity> findAllByOrderByCoordinatorDescFriendlyNameAsc();

    List<ZigbeeDeviceSnapshotEntity> findAllByCoordinatorIdOrderByCoordinatorDescFriendlyNameAsc(Integer coordinatorId);

    long countByCoordinatorIdAndCoordinatorFalse(Integer coordinatorId);
    void deleteByCoordinatorId(Integer coordinatorId);
}
