package ru.growerhub.backend.maintenance.jpa;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoryRetentionStateRepository
        extends JpaRepository<HistoryRetentionStateEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM HistoryRetentionStateEntity state WHERE state.id = :id")
    Optional<HistoryRetentionStateEntity> findLockedById(@Param("id") Integer id);
}
