package ru.growerhub.backend.onboarding.contract;

public record OnboardingStatus(
        String step,
        int coordinatorCount,
        boolean coordinatorConnected,
        boolean firstDeviceSeen,
        boolean zoneCreated,
        boolean automationEnabled,
        boolean completed
) {
}
