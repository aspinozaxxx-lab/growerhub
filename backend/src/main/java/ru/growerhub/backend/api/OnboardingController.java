package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.OnboardingDtos;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorStatus;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSummary;

@RestController
public class OnboardingController {
    private final ZigbeeFacade zigbeeFacade;
    private final AutomationFacade automationFacade;

    public OnboardingController(ZigbeeFacade zigbeeFacade, AutomationFacade automationFacade) {
        this.zigbeeFacade = zigbeeFacade;
        this.automationFacade = automationFacade;
    }

    @GetMapping("/api/onboarding/status")
    public OnboardingDtos.StatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        List<ZigbeeCoordinatorSummary> coordinators = zigbeeFacade.listCoordinators(user);
        AutomationData.Overview automation = automationFacade.getOverview(user);

        boolean connected = coordinators.stream()
                .anyMatch(item -> item.status() == ZigbeeCoordinatorStatus.ONLINE);
        boolean firstDeviceSeen = coordinators.stream()
                .anyMatch(item -> item.firstDeviceSeenAt() != null || item.deviceCount() > 0);
        boolean zoneCreated = !automation.rooms().isEmpty();
        boolean automationEnabled = automation.rooms().stream().anyMatch(room ->
                room.scenarios().stream().anyMatch(AutomationData.ScenarioConfig::enabled)
                        || room.boxes().stream().anyMatch(box ->
                        box.scenarios().stream().anyMatch(AutomationData.ScenarioConfig::enabled))
        );

        return new OnboardingDtos.StatusResponse(
                resolveStep(coordinators.isEmpty(), connected, firstDeviceSeen, zoneCreated),
                coordinators.size(),
                connected,
                firstDeviceSeen,
                zoneCreated,
                automationEnabled
        );
    }

    private String resolveStep(
            boolean coordinatorMissing,
            boolean connected,
            boolean firstDeviceSeen,
            boolean zoneCreated
    ) {
        if (coordinatorMissing) {
            return "CREATE_COORDINATOR";
        }
        if (!connected) {
            return "CONNECT_COORDINATOR";
        }
        if (!firstDeviceSeen) {
            return "ADD_DEVICE";
        }
        if (!zoneCreated) {
            return "CREATE_ZONE";
        }
        return "COMPLETE";
    }
}
