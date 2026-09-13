package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DemoDeviceRepository extends JpaRepository<DemoDeviceEntity, UUID> {
    List<DemoDeviceEntity> findBySpaceId(UUID spaceId);
    long countBySpaceId(UUID spaceId);
    Optional<DemoDeviceEntity> findByTargetId(String targetId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DemoDeviceEntity d where d.targetId = :target")
    Optional<DemoDeviceEntity> lockByTargetId(@Param("target") String target);
    void deleteBySpaceId(UUID spaceId);
}
