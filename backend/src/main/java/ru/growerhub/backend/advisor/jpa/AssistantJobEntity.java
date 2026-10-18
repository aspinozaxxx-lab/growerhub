package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "client_key"}))
public class AssistantJobEntity {
    @Id public UUID id;
    public UUID conversationId;
    public Integer userId;
    public UUID clientKey;
    public String requestHash;
    public String billingKey;
    public String ipKey;
    public String visitorKey;
    public String channel;
    public boolean guest;
    public String provider;
    public String model;
    public String effort;
    public String status;
    @Column(columnDefinition = "text") public String inputText;
    @Column(columnDefinition = "text") public String outputText;
    @Column(columnDefinition = "text") public String attachmentsJson;
    @Column(columnDefinition = "text") public String toolResultsJson;
    public String errorCode;
    public String leaseKey;
    public LocalDateTime leasedUntil;
    public LocalDateTime createdAt;
    public LocalDateTime finishedAt;
    public int toolCalls;
    public boolean dispatched;
    public boolean telegramQueued;
    @Column(columnDefinition = "text") public String reportSummary;
}
