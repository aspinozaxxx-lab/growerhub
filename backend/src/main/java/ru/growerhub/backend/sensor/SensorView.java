package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;
import java.util.List;

public record SensorView(
        Integer id,
        SensorType type,
        Integer channel,
        String label,
        boolean detected,
        Double lastValue,
        LocalDateTime lastTs,
        List<SensorBoundPlantView> boundPlants
) {
}
