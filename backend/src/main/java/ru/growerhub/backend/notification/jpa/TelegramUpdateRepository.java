package ru.growerhub.backend.notification.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface TelegramUpdateRepository extends JpaRepository<TelegramUpdateEntity, Long> {
    void deleteByReceivedAtBefore(LocalDateTime before);
}
