package ru.growerhub.backend.plant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ru.growerhub.backend.user.UserEntity;

@Entity
@Table(name = "plants")
public class PlantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "planted_at", nullable = false)
    private LocalDateTime plantedAt;

    @Column(name = "plant_type", nullable = true, length = 255)
    private String plantType;

    @Column(name = "strain", nullable = true, length = 255)
    private String strain;

    @Column(name = "growth_stage", nullable = true, length = 255)
    private String growthStage;

    @Column(name = "harvested_at", nullable = true)
    private LocalDateTime harvestedAt;

    @ManyToOne
    @JoinColumn(name = "plant_group_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PlantGroupEntity plantGroup;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    protected PlantEntity() {
    }

    public static PlantEntity create() {
        return new PlantEntity();
    }

    public Integer getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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

    public PlantGroupEntity getPlantGroup() {
        return plantGroup;
    }

    public void setPlantGroup(PlantGroupEntity plantGroup) {
        this.plantGroup = plantGroup;
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
