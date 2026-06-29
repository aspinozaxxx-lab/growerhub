package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_scenario_states")
public class AutomationScenarioStateEntity {
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

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "unavailable_reason", columnDefinition = "TEXT")
    private String unavailableReason;

    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    @Column(name = "last_action_at")
    private LocalDateTime lastActionAt;

    @Column(name = "ac_request_active", nullable = false)
    private boolean acRequestActive;

    @Column(name = "manual_pause_until")
    private LocalDateTime manualPauseUntil;

    @Column(name = "runtime_json", columnDefinition = "TEXT")
    private String runtimeJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AutomationScenarioStateEntity() {
    }

    public static AutomationScenarioStateEntity create(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            LocalDateTime now
    ) {
        AutomationScenarioStateEntity entity = new AutomationScenarioStateEntity();
        entity.scopeType = scopeType;
        entity.scopeId = scopeId;
        entity.scenarioType = scenarioType;
        entity.status = "disabled";
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    public LocalDateTime getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(LocalDateTime lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public LocalDateTime getLastActionAt() {
        return lastActionAt;
    }

    public void setLastActionAt(LocalDateTime lastActionAt) {
        this.lastActionAt = lastActionAt;
    }

    public boolean isAcRequestActive() {
        return acRequestActive;
    }

    public void setAcRequestActive(boolean acRequestActive) {
        this.acRequestActive = acRequestActive;
    }

    public LocalDateTime getManualPauseUntil() {
        return manualPauseUntil;
    }

    public void setManualPauseUntil(LocalDateTime manualPauseUntil) {
        this.manualPauseUntil = manualPauseUntil;
    }

    public String getRuntimeJson() {
        return runtimeJson;
    }

    public void setRuntimeJson(String runtimeJson) {
        this.runtimeJson = runtimeJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
