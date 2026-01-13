package ru.growerhub.backend.sensor.contract;

import java.time.LocalDateTime;
import java.util.List;
import ru.growerhub.backend.sensor.SensorBoundPlantView;
import ru.growerhub.backend.sensor.SensorType;

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

