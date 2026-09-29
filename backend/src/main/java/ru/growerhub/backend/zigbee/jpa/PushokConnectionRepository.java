package ru.growerhub.backend.zigbee.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushokConnectionRepository extends JpaRepository<PushokConnectionEntity, Integer> {
    Optional<PushokConnectionEntity> findByHubId(String hubId);
}
