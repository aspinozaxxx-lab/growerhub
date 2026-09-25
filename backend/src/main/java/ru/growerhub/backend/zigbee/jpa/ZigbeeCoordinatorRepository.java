package ru.growerhub.backend.zigbee.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeCoordinatorRepository extends JpaRepository<ZigbeeCoordinatorEntity, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ZigbeeCoordinatorEntity c where c.id = :id and c.archivedAt is null")
    Optional<ZigbeeCoordinatorEntity> lockActiveById(@org.springframework.data.repository.query.Param("id") Integer id);
    Optional<ZigbeeCoordinatorEntity> findByPublicIdAndUserIdAndArchivedAtIsNull(UUID publicId, Integer userId);

    Optional<ZigbeeCoordinatorEntity> findByPublicIdAndArchivedAtIsNull(UUID publicId);

    Optional<ZigbeeCoordinatorEntity> findByIdAndArchivedAtIsNull(Integer id);

    Optional<ZigbeeCoordinatorEntity> findByMqttUsernameAndArchivedAtIsNull(String mqttUsername);

    Optional<ZigbeeCoordinatorEntity> findByBaseTopicAndArchivedAtIsNull(String baseTopic);

    List<ZigbeeCoordinatorEntity> findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(Integer userId);

    List<ZigbeeCoordinatorEntity> findAllByArchivedAtIsNullOrderByCreatedAtAsc();

    Optional<ZigbeeCoordinatorEntity> findFirstByUserIdOrderByCredentialIssuedAtDesc(Integer userId);

    @org.springframework.data.jpa.repository.Query("select c.baseTopic from ZigbeeCoordinatorEntity c where c.executionKind = 'SIMULATED'")
    List<String> findSimulatedBaseTopics();
}
