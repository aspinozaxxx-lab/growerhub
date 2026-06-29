package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_action_log")
public class AutomationActionLogEntity {
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_binding_id")
    private AutomationResourceBindingEntity resourceBinding;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "duration_s")
    private Integer durationS;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AutomationActionLogEntity() {
    }

    public static AutomationActionLogEntity create(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            AutomationResourceBindingEntity resourceBinding,
            String action,
            String reason,
            String result,
            Integer durationS,
            LocalDateTime now
    ) {
        AutomationActionLogEntity entity = new AutomationActionLogEntity();
        entity.scopeType = scopeType;
        entity.scopeId = scopeId;
        entity.scenarioType = scenarioType;
        entity.resourceBinding = resourceBinding;
        entity.action = action;
        entity.reason = reason;
        entity.result = result;
        entity.durationS = durationS;
        entity.createdAt = now;
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

    public AutomationResourceBindingEntity getResourceBinding() {
        return resourceBinding;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getResult() {
        return result;
    }

    public Integer getDurationS() {
        return durationS;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
