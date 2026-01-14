package ru.growerhub.backend.advisor.contract;

import java.time.LocalDateTime;

public record WateringAdvice(
        Boolean isDue,
        Double recommendedWaterVolumeL,
        Double recommendedPh,
        String recommendedFertilizersPerLiter,
        LocalDateTime validUntil
) {
}
