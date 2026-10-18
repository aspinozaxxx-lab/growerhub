package ru.growerhub.backend.notification.jpa;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TelegramInboxRepository extends JpaRepository<TelegramInboxEntity, Long> {
    List<TelegramInboxEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable page);
    List<TelegramInboxEntity> findByStatusAndLeasedUntilBefore(String status, LocalDateTime before);
    long countByUserIdAndCreatedAtAfter(Integer user, LocalDateTime after);
    void deleteByCreatedAtBefore(LocalDateTime before);
}
