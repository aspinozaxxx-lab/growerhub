package ru.growerhub.backend.advisor.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "advisor_watering_advice",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "plant_id")
    }
)
public class AdvisorWateringAdviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "plant_id", nullable = false)
    private Integer plantId;

    @Column(name = "is_due", nullable = false)
    private Boolean isDue;

    @Column(name = "recommended_water_volume_l", nullable = true)
    private Double recommendedWaterVolumeL;

    @Column(name = "recommended_ph", nullable = true)
    private Double recommendedPh;

    @Column(name = "recommended_fertilizers_per_liter", nullable = true, columnDefinition = "TEXT")
    private String recommendedFertilizersPerLiter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    protected AdvisorWateringAdviceEntity() {
    }

    public static AdvisorWateringAdviceEntity create() {
        return new AdvisorWateringAdviceEntity();
    }

    public Integer getId() {
        return id;
    }

    public Integer getPlantId() {
        return plantId;
    }

    public void setPlantId(Integer plantId) {
        this.plantId = plantId;
    }

    public Boolean getIsDue() {
        return isDue;
    }

    public void setIsDue(Boolean isDue) {
        this.isDue = isDue;
    }

    public Double getRecommendedWaterVolumeL() {
        return recommendedWaterVolumeL;
    }

    public void setRecommendedWaterVolumeL(Double recommendedWaterVolumeL) {
        this.recommendedWaterVolumeL = recommendedWaterVolumeL;
    }

    public Double getRecommendedPh() {
        return recommendedPh;
    }

    public void setRecommendedPh(Double recommendedPh) {
        this.recommendedPh = recommendedPh;
    }

    public String getRecommendedFertilizersPerLiter() {
        return recommendedFertilizersPerLiter;
    }

    public void setRecommendedFertilizersPerLiter(String recommendedFertilizersPerLiter) {
        this.recommendedFertilizersPerLiter = recommendedFertilizersPerLiter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }
}
