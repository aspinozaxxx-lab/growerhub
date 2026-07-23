package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class OnboardingDtos {
    private OnboardingDtos() {
    }

    public record StatusResponse(
            @JsonProperty("step") String step,
            @JsonProperty("coordinator_count") int coordinatorCount,
            @JsonProperty("coordinator_connected") boolean coordinatorConnected,
            @JsonProperty("first_device_seen") boolean firstDeviceSeen,
            @JsonProperty("zone_created") boolean zoneCreated,
            @JsonProperty("automation_enabled") boolean automationEnabled
    ) {
    }
}
