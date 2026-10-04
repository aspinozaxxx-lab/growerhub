package ru.growerhub.backend.notification.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "telegram_deliveries")
public class TelegramDeliveryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Integer userId;
    public long chatId;
    public long addressVersion;
    @Column(unique = true, nullable = false) public String dedupeKey;
    @Column(columnDefinition = "TEXT", nullable = false) public String text;
    @Column(columnDefinition = "TEXT") public String keyboard;
    @Column(nullable = false) public String status;
    public LocalDateTime availableAt;
    public LocalDateTime expiresAt;
    public LocalDateTime leasedUntil;
    public String leaseKey;
    public int attempts;
}
