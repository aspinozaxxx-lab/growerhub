package ru.growerhub.backend.shop.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import ru.growerhub.backend.shop.contract.ShopData;

@Entity
@Table(name = "shop_requests")
public class ShopRequestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 36) public String idempotencyKey;
    @Column(nullable = false, length = 64) public String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public ShopData.Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public ShopData.Status status;
    @Column(nullable = false, length = 50) public String catalogVersion;
    @Column(nullable = false, length = 3) public String currency;
    @Column(nullable = false) public long totalMinor;
    @Column(nullable = false, columnDefinition = "TEXT") public String itemsJson;
    @Column(nullable = false, length = 100) public String customerName;
    @Column(nullable = false, length = 32) public String customerPhone;
    @Column(length = 100) public String customerTelegram;
    @Column(length = 120) public String pickupCity;
    @Column(length = 40) public String pickupCode;
    @Column(length = 300) public String pickupAddress;
    @Column(columnDefinition = "TEXT") public String comment;
    @Column(nullable = false, length = 64) public String addressHash;
    @Column(nullable = false, length = 64) public String phoneHash;
    @Column(nullable = false) public LocalDateTime consentAt;
    @Column(nullable = false) public LocalDateTime createdAt;
    @Column(nullable = false) public LocalDateTime updatedAt;
}
