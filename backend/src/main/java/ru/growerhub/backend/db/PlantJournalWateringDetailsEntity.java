package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "plant_journal_watering_details",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "journal_entry_id")
    }
)
public class PlantJournalWateringDetailsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "journal_entry_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantJournalEntryEntity journalEntry;

    @Column(name = "water_volume_l", nullable = false)
    private Double waterVolumeL;

    @Column(name = "duration_s", nullable = false)
    private Integer durationS;

    @Column(name = "ph", nullable = true)
    private Double ph;

    @Lob
    @Column(name = "fertilizers_per_liter", nullable = true)
    private String fertilizersPerLiter;

    protected PlantJournalWateringDetailsEntity() {
    }

    public Integer getId() {
        return id;
    }

    public PlantJournalEntryEntity getJournalEntry() {
        return journalEntry;
    }

    public Double getWaterVolumeL() {
        return waterVolumeL;
    }

    public Integer getDurationS() {
        return durationS;
    }

    public Double getPh() {
        return ph;
    }

    public String getFertilizersPerLiter() {
        return fertilizersPerLiter;
    }
}
