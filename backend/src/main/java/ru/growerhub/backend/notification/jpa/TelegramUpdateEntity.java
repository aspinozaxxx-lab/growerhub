package ru.growerhub.backend.notification.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "telegram_updates")
public class TelegramUpdateEntity {
    @Id public Long id;
    public LocalDateTime receivedAt;
}
