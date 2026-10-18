package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantUsageRepository extends JpaRepository<AssistantUsageEntity, UUID> {
    @Query("select u from AssistantUsageEntity u where u.createdAt >= :since or u.reservedMicros > 0 or u.finishedAt >= :since")
    List<AssistantUsageEntity> window(@Param("since") LocalDateTime since);
}
