package ru.growerhub.backend.automation.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlChannelRepository extends JpaRepository<ControlChannelEntity, UUID> {
    List<ControlChannelEntity> findByUserId(Integer userId);
    Optional<ControlChannelEntity> findByUserIdAndTargetKey(Integer userId, String key);
    List<ControlChannelEntity> findByStatusIn(Collection<String> statuses);
    void deleteByUserId(Integer userId);
}
