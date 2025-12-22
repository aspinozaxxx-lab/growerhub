package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
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

    @ManyToOne
    @JoinColumn(name = "plant_group_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PlantGroupEntity plantGroup;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plant")
    private List<PlantDeviceEntity> plantDevices;

    @OneToMany(mappedBy = "plant")
    private List<PlantJournalEntryEntity> journalEntries;

    protected PlantEntity() {
    }

    public Integer getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getPlantedAt() {
        return plantedAt;
    }

    public String getPlantType() {
        return plantType;
    }

    public String getStrain() {
        return strain;
    }

    public String getGrowthStage() {
        return growthStage;
    }

    public PlantGroupEntity getPlantGroup() {
        return plantGroup;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<PlantDeviceEntity> getPlantDevices() {
        return plantDevices;
    }

    public List<PlantJournalEntryEntity> getJournalEntries() {
        return journalEntries;
    }
}
