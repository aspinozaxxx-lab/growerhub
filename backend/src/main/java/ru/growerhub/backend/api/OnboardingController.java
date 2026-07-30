package ru.growerhub.backend.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.OnboardingDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.onboarding.OnboardingFacade;
import ru.growerhub.backend.onboarding.contract.OnboardingStatus;

@RestController
public class OnboardingController {
    private final OnboardingFacade onboardingFacade;

    public OnboardingController(OnboardingFacade onboardingFacade) {
        this.onboardingFacade = onboardingFacade;
    }

    @GetMapping("/api/onboarding/status")
    public OnboardingDtos.StatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        return toResponse(onboardingFacade.getStatus(user));
    }

    @PostMapping("/api/onboarding/complete")
    public OnboardingDtos.StatusResponse complete(@AuthenticationPrincipal AuthenticatedUser user) {
        return toResponse(onboardingFacade.complete(user));
    }

    private OnboardingDtos.StatusResponse toResponse(OnboardingStatus status) {
        return new OnboardingDtos.StatusResponse(
                status.step(),
                status.coordinatorCount(),
                status.coordinatorConnected(),
                status.firstDeviceSeen(),
                status.zoneCreated(),
                status.automationEnabled(),
                status.completed()
        );
    }
}
