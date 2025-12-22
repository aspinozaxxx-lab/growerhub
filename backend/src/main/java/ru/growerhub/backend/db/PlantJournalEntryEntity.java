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

    public Integer getId() {
        return id;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public PlantJournalWateringDetailsEntity getWateringDetails() {
        return wateringDetails;
    }

    public List<PlantJournalPhotoEntity> getPhotos() {
        return photos;
    }
}
