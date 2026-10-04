package ru.growerhub.backend.notification.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TelegramChannelRepository extends JpaRepository<TelegramChannelEntity, Integer> {
    Optional<TelegramChannelEntity> findByLinkHash(String hash);
    Optional<TelegramChannelEntity> findByChatId(Long chatId);
    Optional<TelegramChannelEntity> findByPendingChatId(Long chatId);
    List<TelegramChannelEntity> findByEnabledTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramChannelEntity c where c.userId = :id")
    Optional<TelegramChannelEntity> lock(@Param("id") Integer id);
}
