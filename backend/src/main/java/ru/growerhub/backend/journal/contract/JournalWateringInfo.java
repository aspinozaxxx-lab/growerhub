package ru.growerhub.backend.journal.contract;

import java.time.LocalDateTime;

public record JournalWateringInfo(
        Double waterVolumeL,
        Integer durationS,
        Double ph,
        String fertilizersPerLiter,
        LocalDateTime eventAt
) {
}
