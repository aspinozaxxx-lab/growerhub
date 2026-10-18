package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_preferences")
public class AssistantPreferencesEntity {
    @Id public Integer userId;
    public boolean telegram;
    public String report;
    @Column(name = "report_hour") public int hour;
    public int weekday;
    public UUID conversationId;
    public String lastReportKey;
    public LocalDateTime updatedAt;
}
