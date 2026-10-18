package ru.growerhub.backend.automation.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "automation_rule_runs")
public class RuleRunEntity {
    @Id public UUID id;
    public UUID ruleId;
    public Integer userId;
    public int revision;
    public String triggerKey;
    @Column(columnDefinition = "TEXT") public String definitionJson;
    @Column(columnDefinition = "TEXT") public String bindingFingerprint;
    public String status;
    public int stepIndex;
    public LocalDateTime nextAt;
    public LocalDateTime leasedUntil;
    public String leaseKey;
    @Column(columnDefinition = "TEXT") public String reason;
    @Column(columnDefinition = "TEXT") public String resultJson;
    public LocalDateTime createdAt;
}
