package ru.growerhub.backend.zigbee.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeDevicePropertyReadingRepository extends JpaRepository<ZigbeeDevicePropertyReadingEntity, Integer> {
    List<ZigbeeDevicePropertyReadingEntity> findAllByCoordinatorIdAndIeeeAddressAndPropertyAndTsGreaterThanEqualOrderByTs(
            Integer coordinatorId,
            String ieeeAddress,
            String property,
            LocalDateTime ts
    );

    List<ZigbeeDevicePropertyReadingEntity> findAllByCoordinatorIdAndFriendlyNameAndPropertyAndTsGreaterThanEqualOrderByTs(
            Integer coordinatorId,
            String friendlyName,
            String property,
            LocalDateTime ts
    );

    List<ZigbeeDevicePropertyReadingEntity> findAllByIeeeAddressAndPropertyAndTsGreaterThanEqualOrderByTs(
            String ieeeAddress,
            String property,
            LocalDateTime ts
    );

    List<ZigbeeDevicePropertyReadingEntity> findAllByFriendlyNameAndPropertyAndTsGreaterThanEqualOrderByTs(
            String friendlyName,
            String property,
            LocalDateTime ts
    );
}
