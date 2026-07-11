package ru.growerhub.backend.api;

import org.springframework.http.HttpStatus;
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
public class AdminManualWateringController {
    private final AutomationFacade automationFacade;

    public AdminManualWateringController(AutomationFacade automationFacade) {
        this.automationFacade = automationFacade;
    }

    @GetMapping("/api/admin/manual-watering")
    public AutomationData.ManualWateringOverview overview(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return automationFacade.getManualWateringOverview();
    }

    @PostMapping("/api/admin/manual-watering/pumps/{pump_id}/start")
    public PumpSessionData.View start(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId,
            @RequestBody AutomationData.ManualWateringStartRequest request
    ) {
        requireAdmin(user);
        return automationFacade.startManualWatering(pumpId, request, user);
    }

    @PostMapping("/api/admin/manual-watering/pumps/{pump_id}/stop")
    public PumpSessionData.View stop(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId
    ) {
        requireAdmin(user);
        return automationFacade.stopManualWatering(pumpId, user);
    }

    @GetMapping("/api/admin/manual-watering/pumps/{pump_id}/sessions")
    public PumpSessionData.Page sessions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("pump_id") Integer pumpId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "before_id", required = false) Long beforeId
    ) {
        requireAdmin(user);
        return automationFacade.getManualWateringSessions(pumpId, limit, beforeId);
    }

    @GetMapping("/api/admin/manual-watering/boxes/{box_id}/statistics")
    public PumpSessionData.BoxStatistics boxStatistics(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("box_id") Integer boxId,
            @RequestParam(value = "range", defaultValue = "day") String range,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "before_id", required = false) Long beforeId
    ) {
        requireAdmin(user);
        return automationFacade.getManualWateringBoxStatistics(boxId, range, limit, beforeId);
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
