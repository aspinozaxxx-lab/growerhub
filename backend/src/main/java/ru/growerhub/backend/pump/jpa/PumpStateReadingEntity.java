package ru.growerhub.backend.pump.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "pump_state_readings",
    indexes = {
        @Index(name = "ix_pump_state_readings_pump_ts", columnList = "pump_id, ts")
    }
)
public class PumpStateReadingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "pump_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PumpEntity pump;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "is_running")
    private Boolean isRunning;

    @Column(name = "raw_status")
    private String rawStatus;

    @Column(name = "raw_state_json", columnDefinition = "TEXT")
    private String rawStateJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PumpStateReadingEntity() {
    }

    public static PumpStateReadingEntity create() {
        return new PumpStateReadingEntity();
    }

    public Integer getId() {
        return id;
    }

    public PumpEntity getPump() {
        return pump;
    }

    public void setPump(PumpEntity pump) {
        this.pump = pump;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public Boolean getIsRunning() {
        return isRunning;
    }

    public void setIsRunning(Boolean isRunning) {
        this.isRunning = isRunning;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public void setRawStatus(String rawStatus) {
        this.rawStatus = rawStatus;
    }

    public String getRawStateJson() {
        return rawStateJson;
    }

    public void setRawStateJson(String rawStateJson) {
        this.rawStateJson = rawStateJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
