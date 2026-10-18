package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "assistant_proposals", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "tool_call_id"}))
public class AssistantProposalEntity {
    public LocalDateTime applyingAt;
    @Id public UUID id;
    public UUID jobId;
    public String toolCallId;
    public Integer userId;
    public String kind;
    public String title;
    @Column(columnDefinition = "text") public String payloadJson;
    public String expectedHash;
    public String revision;
    public String status;
    public boolean telegramQueued;
    @Column(columnDefinition = "text") public String resultJson;
    public LocalDateTime expiresAt;
    public LocalDateTime createdAt;
}
