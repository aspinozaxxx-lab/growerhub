package ru.growerhub.backend.automation.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "automation_rule_versions")
public class RuleVersionEntity {
    @Id public UUID id;
    public UUID ruleId;
    public int revision;
    public boolean enabled;
    @Column(columnDefinition = "TEXT") public String definitionJson;
    public LocalDateTime createdAt;
}
