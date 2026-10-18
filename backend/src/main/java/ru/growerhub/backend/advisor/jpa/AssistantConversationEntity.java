package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_conversations")
public class AssistantConversationEntity {
    @Id public UUID id;
    public Integer userId;
    public String title;
    public String model;
    public String effort;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
