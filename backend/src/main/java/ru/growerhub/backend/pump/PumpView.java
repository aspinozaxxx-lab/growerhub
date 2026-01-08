package ru.growerhub.backend.pump;

import java.util.List;

public record PumpView(
        Integer id,
        Integer channel,
        String label,
        Boolean isRunning,
        List<PumpBoundPlantView> boundPlants
) {
}
