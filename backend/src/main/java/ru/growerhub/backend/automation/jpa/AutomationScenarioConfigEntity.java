package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_scenario_configs")
public class AutomationScenarioConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private Integer scopeId;

    @Column(name = "scenario_type", nullable = false)
    private String scenarioType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AutomationScenarioConfigEntity() {
    }

    public static AutomationScenarioConfigEntity create(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            LocalDateTime now
    ) {
        AutomationScenarioConfigEntity entity = new AutomationScenarioConfigEntity();
        entity.scopeType = scopeType;
        entity.scopeId = scopeId;
        entity.scenarioType = scenarioType;
        entity.enabled = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Integer getScopeId() {
        return scopeId;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
