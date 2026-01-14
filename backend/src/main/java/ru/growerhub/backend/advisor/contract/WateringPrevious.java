package ru.growerhub.backend.advisor.contract;

import java.time.LocalDateTime;

public record WateringPrevious(
        Double waterVolumeL,
        Double ph,
        String fertilizersPerLiter,
        LocalDateTime eventAt
) {
}
