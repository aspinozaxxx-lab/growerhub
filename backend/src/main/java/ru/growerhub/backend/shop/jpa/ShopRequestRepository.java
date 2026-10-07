package ru.growerhub.backend.shop.jpa;

import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.growerhub.backend.shop.contract.ShopData;

public interface ShopRequestRepository extends JpaRepository<ShopRequestEntity, Long> {
    Optional<ShopRequestEntity> findByIdempotencyKey(String key);
    long countByAddressHashAndCreatedAtAfter(String hash, LocalDateTime since);
    long countByPhoneHashAndCreatedAtAfter(String hash, LocalDateTime since);
    Page<ShopRequestEntity> findByStatus(ShopData.Status status, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ShopRequestEntity r where r.id = :id")
    Optional<ShopRequestEntity> lock(@Param("id") Long id);
}
