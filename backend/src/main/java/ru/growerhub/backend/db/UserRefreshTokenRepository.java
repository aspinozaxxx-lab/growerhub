package ru.growerhub.backend.db;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshTokenEntity, Integer> {
    Optional<UserRefreshTokenEntity> findByTokenHash(String tokenHash);
}
