package ru.growerhub.backend.notification.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "telegram_channels")
public class TelegramChannelEntity {
    @Id public Integer userId;
    @Column(unique = true) public Long chatId;
    public String name;
    @Column(unique = true, length = 64) public String linkHash;
    public LocalDateTime linkExpiresAt;
    @Column(unique = true) public Long pendingChatId;
    public String pendingName;
    public String confirmation;
    public boolean enabled;
    public int digestHour;
    public int quietFrom;
    public int quietUntil;
    public long addressVersion;
    public String lastStatus;
    public LocalDateTime lastTestAt;
}
