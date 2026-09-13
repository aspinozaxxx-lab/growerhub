package ru.growerhub.backend.auth.jpa;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoSessionRepository extends JpaRepository<DemoSessionEntity, UUID> {
    Optional<DemoSessionEntity> findByRefreshHash(String hash);
    void deleteBySpaceId(UUID spaceId);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
