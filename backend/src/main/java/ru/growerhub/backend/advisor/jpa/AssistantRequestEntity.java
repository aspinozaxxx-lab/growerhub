package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_feature_requests")
public class AssistantRequestEntity {
    @Id public UUID id;
    public Integer userId;
    public String title;
    @Column(columnDefinition = "text") public String description;
    @Column(columnDefinition = "text") public String benefit;
    public String status;
    @Column(columnDefinition = "text") public String note;
    public int revision;
    public boolean notificationQueued;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
