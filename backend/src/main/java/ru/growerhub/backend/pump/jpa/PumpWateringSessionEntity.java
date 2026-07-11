package ru.growerhub.backend.pump.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pump_watering_sessions",
        indexes = {
                @Index(name = "ix_pump_watering_sessions_pump_id", columnList = "pump_id,id"),
                @Index(name = "ix_pump_watering_sessions_finished_at", columnList = "finished_at")
        }
)
public class PumpWateringSessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "pump_id")
    private Integer pumpId;

    @Column(name = "device_id")
    private Integer deviceId;

    @Column(name = "device_key", nullable = false)
    private String deviceKey;

    @Column(name = "active_device_key", unique = true)
    private String activeDeviceKey;

    @Column(name = "channel", nullable = false)
    private Integer channel;

    @Column(name = "pump_label")
    private String pumpLabel;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "phase", nullable = false)
    private String phase;

    @Column(name = "stopping_target_phase")
    private String stoppingTargetPhase;

    @Column(name = "planned_duration_s")
    private Integer plannedDurationS;

    @Column(name = "max_active_duration_s")
    private Integer maxActiveDurationS;

    @Column(name = "pulse_enabled", nullable = false)
    private boolean pulseEnabled;

    @Column(name = "pulse_run_s")
    private Integer pulseRunS;

    @Column(name = "pulse_pause_s")
    private Integer pulsePauseS;

    @Column(name = "active_duration_s", nullable = false)
    private int activeDurationS;

    @Column(name = "journal_eligible", nullable = false)
    private boolean journalEligible;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "phase_started_at", nullable = false)
    private LocalDateTime phaseStartedAt;

    @Column(name = "last_command_at")
    private LocalDateTime lastCommandAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "completion_reason")
    private String completionReason;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "planned_water_volume_l")
    private Double plannedWaterVolumeL;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "fertilizers_per_liter", columnDefinition = "TEXT")
    private String fertilizersPerLiter;

    protected PumpWateringSessionEntity() {
    }

    public static PumpWateringSessionEntity create() {
        return new PumpWateringSessionEntity();
    }

    public Long getId() { return id; }
    public Integer getPumpId() { return pumpId; }
    public void setPumpId(Integer pumpId) { this.pumpId = pumpId; }
    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }
    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }
    public String getActiveDeviceKey() { return activeDeviceKey; }
    public void setActiveDeviceKey(String activeDeviceKey) { this.activeDeviceKey = activeDeviceKey; }
    public Integer getChannel() { return channel; }
    public void setChannel(Integer channel) { this.channel = channel; }
    public String getPumpLabel() { return pumpLabel; }
    public void setPumpLabel(String pumpLabel) { this.pumpLabel = pumpLabel; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getStoppingTargetPhase() { return stoppingTargetPhase; }
    public void setStoppingTargetPhase(String stoppingTargetPhase) { this.stoppingTargetPhase = stoppingTargetPhase; }
    public Integer getPlannedDurationS() { return plannedDurationS; }
    public void setPlannedDurationS(Integer plannedDurationS) { this.plannedDurationS = plannedDurationS; }
    public Integer getMaxActiveDurationS() { return maxActiveDurationS; }
    public void setMaxActiveDurationS(Integer maxActiveDurationS) { this.maxActiveDurationS = maxActiveDurationS; }
    public boolean isPulseEnabled() { return pulseEnabled; }
    public void setPulseEnabled(boolean pulseEnabled) { this.pulseEnabled = pulseEnabled; }
    public Integer getPulseRunS() { return pulseRunS; }
    public void setPulseRunS(Integer pulseRunS) { this.pulseRunS = pulseRunS; }
    public Integer getPulsePauseS() { return pulsePauseS; }
    public void setPulsePauseS(Integer pulsePauseS) { this.pulsePauseS = pulsePauseS; }
    public int getActiveDurationS() { return activeDurationS; }
    public void setActiveDurationS(int activeDurationS) { this.activeDurationS = activeDurationS; }
    public boolean isJournalEligible() { return journalEligible; }
    public void setJournalEligible(boolean journalEligible) { this.journalEligible = journalEligible; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getPhaseStartedAt() { return phaseStartedAt; }
    public void setPhaseStartedAt(LocalDateTime phaseStartedAt) { this.phaseStartedAt = phaseStartedAt; }
    public LocalDateTime getLastCommandAt() { return lastCommandAt; }
    public void setLastCommandAt(LocalDateTime lastCommandAt) { this.lastCommandAt = lastCommandAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getCompletionReason() { return completionReason; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Double getPlannedWaterVolumeL() { return plannedWaterVolumeL; }
    public void setPlannedWaterVolumeL(Double plannedWaterVolumeL) { this.plannedWaterVolumeL = plannedWaterVolumeL; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public String getFertilizersPerLiter() { return fertilizersPerLiter; }
    public void setFertilizersPerLiter(String fertilizersPerLiter) { this.fertilizersPerLiter = fertilizersPerLiter; }
}
