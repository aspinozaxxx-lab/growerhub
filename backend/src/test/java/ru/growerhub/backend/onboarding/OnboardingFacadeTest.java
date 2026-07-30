package ru.growerhub.backend.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.onboarding.contract.OnboardingStatus;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.contract.UserProfile;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorStatus;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSummary;

class OnboardingFacadeTest {
    @Test
    void completionRemainsStableWhenCurrentResourcesDisappear() {
        ZigbeeFacade zigbeeFacade = mock(ZigbeeFacade.class);
        AutomationFacade automationFacade = mock(AutomationFacade.class);
        UserFacade userFacade = mock(UserFacade.class);
        OnboardingFacade facade = new OnboardingFacade(zigbeeFacade, automationFacade, userFacade);
        AuthenticatedUser user = new AuthenticatedUser(7, "user");
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 7, 0);

        when(userFacade.getUser(7))
                .thenReturn(profile(null, now))
                .thenReturn(profile(now, now));
        when(zigbeeFacade.listCoordinators(user))
                .thenReturn(List.of(coordinator(now)))
                .thenReturn(List.of());
        when(automationFacade.getFarmsOverview(user))
                .thenReturn(overviewWithGreenhouse(now))
                .thenReturn(emptyOverview());

        OnboardingStatus completed = facade.complete(user);
        assertTrue(completed.completed());
        verify(userFacade).markOnboardingCompleted(7);

        OnboardingStatus later = facade.getStatus(user);
        assertEquals("COMPLETE", later.step());
        assertTrue(later.completed());
    }

    private UserProfile profile(LocalDateTime completedAt, LocalDateTime now) {
        return new UserProfile(
                7,
                "user@example.com",
                "user",
                "user",
                true,
                "Europe/Moscow",
                completedAt,
                now,
                now
        );
    }

    private ZigbeeCoordinatorSummary coordinator(LocalDateTime now) {
        return new ZigbeeCoordinatorSummary(
                UUID.randomUUID(),
                "Coordinator",
                "mqtt-user",
                "gh/z2m/test",
                ZigbeeCoordinatorStatus.ONLINE,
                1,
                now,
                now,
                now,
                now,
                now
        );
    }

    private AutomationData.FarmsOverview overviewWithGreenhouse(LocalDateTime now) {
        AutomationData.Greenhouse greenhouse = new AutomationData.Greenhouse(
                11,
                10,
                "Greenhouse",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                now,
                now
        );
        AutomationData.UserFarm farm = new AutomationData.UserFarm(
                10,
                "Farm",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(greenhouse),
                List.of(),
                now,
                now
        );
        return new AutomationData.FarmsOverview(
                List.of(farm),
                emptyCatalog(),
                List.of(),
                settings()
        );
    }

    private AutomationData.FarmsOverview emptyOverview() {
        return new AutomationData.FarmsOverview(List.of(), emptyCatalog(), List.of(), settings());
    }

    private AutomationData.ResourceCatalog emptyCatalog() {
        return new AutomationData.ResourceCatalog(List.of(), List.of(), List.of());
    }

    private AutomationData.Settings settings() {
        return new AutomationData.Settings("Europe/Moscow", 15, 15, 5);
    }
}
