package ru.growerhub.backend.shop.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_notifications")
public class ShopNotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public Long requestId;
    @Column(nullable = false, length = 20) public String status;
    @Column(nullable = false) public int attempts;
    @Column(nullable = false) public int retryAttempts;
    @Column(length = 40) public String lastError;
    @Column(nullable = false) public LocalDateTime availableAt;
    @Column(nullable = false) public LocalDateTime updatedAt;
    public LocalDateTime leasedUntil;
    @Column(length = 36) public String leaseKey;
    public Long chatId;
}
