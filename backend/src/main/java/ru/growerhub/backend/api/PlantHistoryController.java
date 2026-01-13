package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.HistoryDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.plant.PlantFacade;

@RestController
public class PlantHistoryController {
    private final PlantFacade plantFacade;

    public PlantHistoryController(PlantFacade plantFacade) {
        this.plantFacade = plantFacade;
    }

    @GetMapping("/api/plants/{plant_id}/history")
    public List<HistoryDtos.PlantMetricPointResponse> getPlantHistory(
            @PathVariable("plant_id") Integer plantId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @RequestParam(value = "metrics", defaultValue = "SOIL_MOISTURE") String metrics,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return plantFacade.getHistory(plantId, hours, metrics, user).stream()
                .map(point -> new HistoryDtos.PlantMetricPointResponse(
                        point.metricType(),
                        point.ts(),
                        point.value()
                ))
                .toList();
    }
}


