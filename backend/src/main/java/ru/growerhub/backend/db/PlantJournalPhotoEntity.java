package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "plant_journal_photos")
public class PlantJournalPhotoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "journal_entry_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantJournalEntryEntity journalEntry;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "caption", nullable = true)
    private String caption;

    @Lob
    @Column(name = "data", nullable = true)
    private byte[] data;

    @Column(name = "content_type", nullable = true)
    private String contentType;

    protected PlantJournalPhotoEntity() {
    }

    public Integer getId() {
        return id;
    }

    public PlantJournalEntryEntity getJournalEntry() {
        return journalEntry;
    }

    public String getUrl() {
        return url;
    }

    public String getCaption() {
        return caption;
    }

    public byte[] getData() {
        return data;
    }

    public String getContentType() {
        return contentType;
    }
}
