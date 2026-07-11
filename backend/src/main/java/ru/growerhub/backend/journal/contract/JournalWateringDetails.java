package ru.growerhub.backend.journal.contract;

public record JournalWateringDetails(
        Double waterVolumeL,
        Integer durationS,
        Double ph,
        String fertilizersPerLiter,
        Long pumpSessionId,
        String mode,
        String completionReason
) {
}
