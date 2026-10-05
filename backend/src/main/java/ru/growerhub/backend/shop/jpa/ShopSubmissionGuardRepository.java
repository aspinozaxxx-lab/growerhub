package ru.growerhub.backend.shop.jpa;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ShopSubmissionGuardRepository extends JpaRepository<ShopSubmissionGuardEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ShopSubmissionGuardEntity g where g.id = 1")
    Optional<ShopSubmissionGuardEntity> lock();
}
