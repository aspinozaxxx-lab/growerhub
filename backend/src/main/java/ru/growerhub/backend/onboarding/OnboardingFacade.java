package ru.growerhub.backend.onboarding;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.onboarding.contract.OnboardingStatus;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.contract.UserProfile;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorStatus;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSummary;

@Service
public class OnboardingFacade {
    private final ZigbeeFacade zigbeeFacade;
    private final AutomationFacade automationFacade;
    private final UserFacade userFacade;

    public OnboardingFacade(
            ZigbeeFacade zigbeeFacade,
            AutomationFacade automationFacade,
            UserFacade userFacade
    ) {
        this.zigbeeFacade = zigbeeFacade;
        this.automationFacade = automationFacade;
        this.userFacade = userFacade;
    }

    @Transactional(readOnly = true)
    public OnboardingStatus getStatus(AuthenticatedUser user) {
        requireAuthenticated(user);
        return buildStatus(user);
    }

    @Transactional
    public OnboardingStatus complete(AuthenticatedUser user) {
        requireAuthenticated(user);
        OnboardingStatus status = buildStatus(user);
        if (!status.firstDeviceSeen() || !status.zoneCreated()) {
            throw new DomainException("conflict", "Pervichnaja nastrojka eshche ne zavershena");
        }
        userFacade.markOnboardingCompleted(user.id());
        return new OnboardingStatus(
                "COMPLETE",
                status.coordinatorCount(),
                status.coordinatorConnected(),
                status.firstDeviceSeen(),
                status.zoneCreated(),
                status.automationEnabled(),
                true
        );
    }

    private OnboardingStatus buildStatus(AuthenticatedUser user) {
        UserProfile profile = userFacade.getUser(user.id());
        if (profile == null) {
            throw new DomainException("unauthorized", "Neobhodimo vojti v akkaunt");
        }
        List<ZigbeeCoordinatorSummary> coordinators = zigbeeFacade.listCoordinators(user);
        AutomationData.FarmsOverview automation = automationFacade.getFarmsOverview(user);
        boolean connected = coordinators.stream()
                .anyMatch(item -> item.status() == ZigbeeCoordinatorStatus.ONLINE);
        boolean firstDeviceSeen = coordinators.stream()
                .anyMatch(item -> item.firstDeviceSeenAt() != null || item.deviceCount() > 0);
        List<AutomationData.Greenhouse> greenhouses = automation.farms().stream()
                .flatMap(farm -> farm.greenhouses().stream())
                .toList();
        boolean zoneCreated = !greenhouses.isEmpty();
        boolean automationEnabled = greenhouses.stream().anyMatch(greenhouse ->
                greenhouse.scenarios().stream().anyMatch(AutomationData.ScenarioConfig::enabled)
        );
        boolean completed = profile.onboardingCompletedAt() != null;
        return new OnboardingStatus(
                completed
                        ? "COMPLETE"
                        : resolveStep(coordinators.isEmpty(), connected, firstDeviceSeen, zoneCreated),
                coordinators.size(),
                connected,
                firstDeviceSeen,
                zoneCreated,
                automationEnabled,
                completed
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

    private void requireAuthenticated(AuthenticatedUser user) {
        if (user == null || user.id() == null) {
            throw new DomainException("unauthorized", "Neobhodimo vojti v akkaunt");
        }
    }
}
