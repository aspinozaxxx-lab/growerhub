package ru.growerhub.backend.notification.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name = "telegram_inbox")
public class TelegramInboxEntity {
    @Id public Long id;
    public Integer userId;
    public long addressVersion;
    @Column(columnDefinition = "text") public String text;
    public String photoId;
    public String callbackData;
    public String status;
    public String leaseKey;
    public LocalDateTime leasedUntil;
    public LocalDateTime createdAt;
}
