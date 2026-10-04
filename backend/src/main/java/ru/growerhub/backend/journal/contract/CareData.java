package ru.growerhub.backend.journal.contract;

import java.time.LocalDateTime;
import java.util.List;

public final class CareData {
    private CareData() {}
    public record EntryCommand(String action, String text, LocalDateTime eventAt, String clientKey) {}
    public record Item(JournalEntry entry, String plantName, String action, boolean editable) {}
    public record Page(List<Item> items, boolean hasMore) {}
    public record Summary(Integer plantId, Integer coverPhotoId, LocalDateTime lastWateringAt,
            LocalDateTime lastEntryAt, long entries) {}
    public record ReminderCommand(Integer plantId, String action, String title, LocalDateTime dueAt,
            Integer repeatDays, String repeatMode) {}
    public record Reminder(Integer id, Integer plantId, String plantName, String action, String title,
            LocalDateTime dueAt, Integer repeatDays, String repeatMode, String occurrenceKey, boolean enabled) {}
    public record ReminderAction(String occurrenceKey, LocalDateTime dueAt) {}
}
