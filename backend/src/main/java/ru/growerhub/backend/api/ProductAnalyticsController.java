package ru.growerhub.backend.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.ProductAnalyticsDtos;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeProductAnalytics;

@RestController
public class ProductAnalyticsController {
    private final UserFacade userFacade;
    private final ZigbeeFacade zigbeeFacade;
    private final AutomationFacade automationFacade;

    public ProductAnalyticsController(
            UserFacade userFacade,
            ZigbeeFacade zigbeeFacade,
            AutomationFacade automationFacade
    ) {
        this.userFacade = userFacade;
        this.zigbeeFacade = zigbeeFacade;
        this.automationFacade = automationFacade;
    }

    @GetMapping("/api/admin/product-analytics")
    public ProductAnalyticsDtos.Response get(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        UserFacade.ProductAnalyticsSnapshot users = userFacade.getProductAnalytics();
        ZigbeeProductAnalytics zigbee = zigbeeFacade.getProductAnalytics();
        AutomationData.ProductAnalyticsSnapshot automation = automationFacade.getProductAnalytics();
        Set<Integer> registrations = users.registeredUserIds();

        return new ProductAnalyticsDtos.Response(
                LocalDateTime.now(ZoneOffset.UTC),
                registrations.size(),
                countRegistered(registrations, zigbee.usersWithCoordinator()),
                countRegistered(registrations, zigbee.usersWithConnectedCoordinator()),
                countRegistered(registrations, zigbee.usersWithFirstDevice()),
                countRegistered(registrations, automation.usersWithZone()),
                countRegistered(registrations, automation.usersWithAutomation()),
                zigbee.coordinatorsCreated(),
                zigbee.coordinatorsConnected(),
                automation.zonesCreated(),
                automation.automationsEnabled(),
                zigbee.activeCoordinators1d(),
                zigbee.activeCoordinators7d(),
                zigbee.activeCoordinators28d()
        );
    }

    private long countRegistered(Set<Integer> registrations, Set<Integer> stageUsers) {
        Set<Integer> intersection = new HashSet<>(stageUsers);
        intersection.retainAll(registrations);
        return intersection.size();
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
