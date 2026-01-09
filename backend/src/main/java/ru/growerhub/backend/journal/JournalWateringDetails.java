package ru.growerhub.backend.journal;

public record JournalWateringDetails(
        Double waterVolumeL,
        Integer durationS,
        Double ph,
        String fertilizersPerLiter
) {
}
