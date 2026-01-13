package ru.growerhub.backend.pump.contract;

import java.time.LocalDateTime;

public record PumpBoundPlantView(
        Integer id,
        String name,
        LocalDateTime plantedAt,
        Integer ageDays,
        Integer rateMlPerHour
) {
}

