package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_usage")
public class AssistantUsageEntity {
    @Id public UUID id;
    public String billingKey;
    public String ipKey;
    public String visitorKey;
    public boolean guest;
    public boolean paid;
    public String status;
    public long reservedMicros;
    public long chargedMicros;
    public long inputTokens;
    public long outputTokens;
    public LocalDateTime createdAt;
    public LocalDateTime finishedAt;
}
