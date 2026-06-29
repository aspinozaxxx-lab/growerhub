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
@Table(name = "automation_box_plants")
public class AutomationBoxPlantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private AutomationBoxEntity box;

    @Column(name = "plant_id", nullable = false)
    private Integer plantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AutomationBoxPlantEntity() {
    }

    public static AutomationBoxPlantEntity create(AutomationBoxEntity box, Integer plantId, LocalDateTime now) {
        AutomationBoxPlantEntity entity = new AutomationBoxPlantEntity();
        entity.box = box;
        entity.plantId = plantId;
        entity.createdAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public AutomationBoxEntity getBox() {
        return box;
    }

    public Integer getBoxId() {
        return box != null ? box.getId() : null;
    }

    public Integer getPlantId() {
        return plantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
