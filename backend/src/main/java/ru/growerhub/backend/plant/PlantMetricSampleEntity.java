package ru.growerhub.backend.plant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "plant_metric_samples",
    indexes = {
        @Index(name = "ix_plant_metric_samples_id", columnList = "id"),
        @Index(name = "ix_plant_metric_samples_plant_metric_ts", columnList = "plant_id, metric_type, ts")
    }
)
public class PlantMetricSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "plant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantEntity plant;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false)
    private PlantMetricType metricType;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "value_numeric", nullable = true)
    private Double valueNumeric;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    protected PlantMetricSampleEntity() {
    }

    public static PlantMetricSampleEntity create() {
        return new PlantMetricSampleEntity();
    }

    public Integer getId() {
        return id;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }

    public PlantMetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(PlantMetricType metricType) {
        this.metricType = metricType;
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
