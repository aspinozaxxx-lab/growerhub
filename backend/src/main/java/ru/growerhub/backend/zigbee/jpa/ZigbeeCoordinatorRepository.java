package ru.growerhub.backend.zigbee.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeCoordinatorRepository extends JpaRepository<ZigbeeCoordinatorEntity, Integer> {
    Optional<ZigbeeCoordinatorEntity> findByPublicIdAndUserIdAndArchivedAtIsNull(UUID publicId, Integer userId);

    Optional<ZigbeeCoordinatorEntity> findByPublicIdAndArchivedAtIsNull(UUID publicId);

    Optional<ZigbeeCoordinatorEntity> findByIdAndArchivedAtIsNull(Integer id);

    Optional<ZigbeeCoordinatorEntity> findByMqttUsernameAndArchivedAtIsNull(String mqttUsername);

    Optional<ZigbeeCoordinatorEntity> findByBaseTopicAndArchivedAtIsNull(String baseTopic);

    List<ZigbeeCoordinatorEntity> findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(Integer userId);

    List<ZigbeeCoordinatorEntity> findAllByArchivedAtIsNullOrderByCreatedAtAsc();

    Optional<ZigbeeCoordinatorEntity> findFirstByUserIdOrderByCredentialIssuedAtDesc(Integer userId);
}
