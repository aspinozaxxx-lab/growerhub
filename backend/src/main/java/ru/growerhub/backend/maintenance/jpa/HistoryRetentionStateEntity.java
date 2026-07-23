package ru.growerhub.backend.maintenance.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "history_retention_state")
public class HistoryRetentionStateEntity {
    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "next_day")
    private LocalDate nextDay;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HistoryRetentionStateEntity() {
    }

    public static HistoryRetentionStateEntity create(Integer id, LocalDateTime now) {
        HistoryRetentionStateEntity entity = new HistoryRetentionStateEntity();
        entity.id = id;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public LocalDate getNextDay() {
        return nextDay;
    }

    public void setNextDay(LocalDate nextDay) {
        this.nextDay = nextDay;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
