package ru.growerhub.backend.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.pump.contract.PumpSessionData;

@RestController
@Validated
public class ManualWateringController {
    private final AutomationFacade automationFacade;

    public ManualWateringController(AutomationFacade automationFacade) {
        this.automationFacade = automationFacade;
    }

    @GetMapping("/api/manual-watering")
    public AutomationData.ManualWateringOverview overview(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return automationFacade.getManualWateringOverview(user);
    }

    @PostMapping("/api/manual-watering/pumps/{pump_id}/start")
    public PumpSessionData.View start(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId,
            @RequestBody AutomationData.ManualWateringStartRequest request
    ) {
        return automationFacade.startUserManualWateringSession(pumpId, request, user);
    }

    @PostMapping("/api/manual-watering/pumps/{pump_id}/stop")
    public PumpSessionData.View stop(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId
    ) {
        return automationFacade.stopUserManualWatering(pumpId, user);
    }

    @GetMapping("/api/manual-watering/pumps/{pump_id}/sessions")
    public PumpSessionData.Page sessions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "before_id", required = false) Long beforeId
    ) {
        return automationFacade.getUserManualWateringSessions(pumpId, limit, beforeId, user);
    }

    @GetMapping("/api/manual-watering/greenhouses/{greenhouse_id}/statistics")
    public PumpSessionData.BoxStatistics greenhouseStatistics(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("greenhouse_id") Integer greenhouseId,
            @RequestParam(value = "range", defaultValue = "day") String range,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "before_id", required = false) Long beforeId
    ) {
        return automationFacade.getUserManualWateringBoxStatistics(
                greenhouseId,
                range,
                limit,
                beforeId,
                user
        );
    }
}
