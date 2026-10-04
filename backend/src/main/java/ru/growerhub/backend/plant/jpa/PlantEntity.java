package ru.growerhub.backend.plant.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "plants")
public class PlantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = true)
    private Integer userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "planted_at", nullable = true)
    private LocalDateTime plantedAt;

    @Column(name = "plant_type", nullable = true, length = 255)
    private String plantType;

    @Column(name = "strain", nullable = true, length = 255)
    private String strain;

    @Column(name = "growth_stage", nullable = true, length = 255)
    private String growthStage;

    @Column(name = "harvested_at", nullable = true)
    private LocalDateTime harvestedAt;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_label")
    private String locationLabel;

    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getLocationLabel() { return locationLabel; }
    public void setLocationLabel(String value) { locationLabel = value; }

    protected PlantEntity() {
    }

    public static PlantEntity create() {
        return new PlantEntity();
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getPlantedAt() {
        return plantedAt;
    }

    public void setPlantedAt(LocalDateTime plantedAt) {
        this.plantedAt = plantedAt;
    }

    public String getPlantType() {
        return plantType;
    }

    public void setPlantType(String plantType) {
        this.plantType = plantType;
    }

    public String getStrain() {
        return strain;
    }

    public void setStrain(String strain) {
        this.strain = strain;
    }

    public String getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(String growthStage) {
        this.growthStage = growthStage;
    }

    public LocalDateTime getHarvestedAt() {
        return harvestedAt;
    }

    public void setHarvestedAt(LocalDateTime harvestedAt) {
        this.harvestedAt = harvestedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}



