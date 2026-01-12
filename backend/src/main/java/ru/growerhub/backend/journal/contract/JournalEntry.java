package ru.growerhub.backend.journal.contract;

import java.time.LocalDateTime;
import java.util.List;
import ru.growerhub.backend.journal.JournalPhoto;
import ru.growerhub.backend.journal.JournalWateringDetails;

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
