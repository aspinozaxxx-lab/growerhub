package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantPhotoRepository extends JpaRepository<AssistantPhotoEntity, UUID> {
    List<AssistantPhotoEntity> findByUserId(Integer userId);
    long countByBillingKeyAndCreatedAtAfter(String key, LocalDateTime after);
    long countByIpKeyAndCreatedAtAfter(String key, LocalDateTime after);
    @Query("select coalesce(sum(p.size),0) from AssistantPhotoEntity p")
    long storedBytes();
    @Modifying @Query("delete from AssistantPhotoEntity p where p.createdAt < :before")
    void purge(@Param("before") LocalDateTime before);
}
