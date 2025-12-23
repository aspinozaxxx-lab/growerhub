package ru.growerhub.backend.db;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ru.growerhub.backend.user.UserEntity;

@Entity
@Table(name = "plant_journal_entries")
public class PlantJournalEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "plant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantEntity plant;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity user;

    @Column(name = "type", nullable = false)
    private String type;

    @Lob
    @Column(name = "text", nullable = true)
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

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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
