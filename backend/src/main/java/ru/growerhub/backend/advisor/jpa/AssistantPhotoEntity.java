package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_photos")
public class AssistantPhotoEntity {
    @Id public UUID id;
    public Integer userId;
    public UUID conversationId;
    public String billingKey;
    public String ipKey;
    public String status;
    public long size;
    @Column(columnDefinition = "bytea") public byte[] bytes;
    public LocalDateTime createdAt;
}
