package ru.growerhub.backend.journal.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "plant_care_reminders")
public class CareReminderEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(nullable = false) public Integer userId;
    @Column(nullable = false) public Integer plantId;
    @Column(nullable = false, length = 32) public String action;
    @Column(nullable = false, length = 200) public String title;
    @Column(nullable = false) public LocalDateTime dueAt;
    public Integer repeatDays;
    @Column(nullable = false, length = 32) public String repeatMode;
    @Column(nullable = false, length = 32) public String occurrenceKey;
    @Column(nullable = false) public boolean enabled = true;
    @Version public long version;
}
