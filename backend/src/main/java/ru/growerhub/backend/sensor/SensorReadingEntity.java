package ru.growerhub.backend.sensor;

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
    name = "sensor_readings",
    indexes = {
        @Index(name = "ix_sensor_readings_id", columnList = "id"),
        @Index(name = "ix_sensor_readings_sensor_ts", columnList = "sensor_id, ts")
    }
)
public class SensorReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "sensor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SensorEntity sensor;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "value_numeric", nullable = true)
    private Double valueNumeric;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    protected SensorReadingEntity() {
    }

    public static SensorReadingEntity create() {
        return new SensorReadingEntity();
    }

    public Integer getId() {
        return id;
    }

    public SensorEntity getSensor() {
        return sensor;
    }

    public void setSensor(SensorEntity sensor) {
        this.sensor = sensor;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public Double getValueNumeric() {
        return valueNumeric;
    }

    public void setValueNumeric(Double valueNumeric) {
        this.valueNumeric = valueNumeric;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
