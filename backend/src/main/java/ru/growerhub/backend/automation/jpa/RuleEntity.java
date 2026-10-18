package ru.growerhub.backend.automation.jpa;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name = "automation_rules")
public class RuleEntity {
    @Id public UUID id;
    public Integer userId;
    public int revision;
    public boolean enabled;
    @Column(columnDefinition = "TEXT") public String definitionJson;
    @Column(columnDefinition = "TEXT") public String runtimeJson;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
