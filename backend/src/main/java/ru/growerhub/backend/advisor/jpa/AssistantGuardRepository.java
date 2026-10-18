package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AssistantGuardRepository extends JpaRepository<AssistantGuardEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AssistantGuardEntity g where g.id = :id")
    Optional<AssistantGuardEntity> lock(@Param("id") Integer id);
}
