package ru.growerhub.backend.journal.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "plant_journal_entries")
public class PlantJournalEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "plant_id", nullable = false)
    private Integer plantId;

    @Column(name = "user_id", nullable = true)
    private Integer userId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "text", nullable = true, columnDefinition = "TEXT")
    private String text;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "created_at", nullable = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    @OneToOne(
        mappedBy = "journalEntry",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private PlantJournalWateringDetailsEntity wateringDetails;

    @OneToMany(mappedBy = "journalEntry")
    private List<PlantJournalPhotoEntity> photos;

    protected PlantJournalEntryEntity() {
    }

    public static PlantJournalEntryEntity create() {
        return new PlantJournalEntryEntity();
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

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
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

    public PlantJournalWateringDetailsEntity getWateringDetails() {
        return wateringDetails;
    }

    public void setWateringDetails(PlantJournalWateringDetailsEntity wateringDetails) {
        this.wateringDetails = wateringDetails;
    }

    public List<PlantJournalPhotoEntity> getPhotos() {
        return photos;
    }
}
