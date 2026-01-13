package ru.growerhub.backend.pump.contract;

import java.util.List;
import ru.growerhub.backend.pump.PumpBoundPlantView;

public record PumpView(
        Integer id,
        Integer channel,
        String label,
        Boolean isRunning,
        List<PumpBoundPlantView> boundPlants
) {
}

