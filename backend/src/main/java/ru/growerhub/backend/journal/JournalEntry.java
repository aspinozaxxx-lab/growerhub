package ru.growerhub.backend.journal;

import java.time.LocalDateTime;
import java.util.List;

public record JournalEntry(
        Integer id,
        Integer plantId,
        Integer userId,
        String type,
        String text,
        LocalDateTime eventAt,
        LocalDateTime createdAt,
        List<JournalPhoto> photos,
        JournalWateringDetails wateringDetails
) {
}
