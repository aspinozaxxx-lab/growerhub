package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
public interface AssistantConversationRepository extends JpaRepository<AssistantConversationEntity, UUID> {
    List<AssistantConversationEntity> findByUserIdOrderByUpdatedAtDesc(Integer userId, Pageable page);
    Optional<AssistantConversationEntity> findByIdAndUserId(UUID id, Integer userId);
    long countByUserIdAndCreatedAtAfter(Integer userId, LocalDateTime after);
    @Modifying @Query("delete from AssistantConversationEntity c where c.updatedAt < :before and not exists (select j.id from AssistantJobEntity j where j.conversationId = c.id)")
    void purge(@Param("before") LocalDateTime before);
}
