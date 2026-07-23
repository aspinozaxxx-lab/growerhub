package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class ProductAnalyticsDtos {
    private ProductAnalyticsDtos() {
    }

    public record Response(
            @JsonProperty("generated_at") LocalDateTime generatedAt,
            @JsonProperty("registrations") long registrations,
            @JsonProperty("users_with_coordinator") long usersWithCoordinator,
            @JsonProperty("users_with_connected_coordinator") long usersWithConnectedCoordinator,
            @JsonProperty("users_with_first_device") long usersWithFirstDevice,
            @JsonProperty("users_with_zone") long usersWithZone,
            @JsonProperty("users_with_automation") long usersWithAutomation,
            @JsonProperty("coordinators_created") long coordinatorsCreated,
            @JsonProperty("coordinators_connected") long coordinatorsConnected,
            @JsonProperty("zones_created") long zonesCreated,
            @JsonProperty("automations_enabled") long automationsEnabled,
            @JsonProperty("active_coordinators_1d") long activeCoordinators1d,
            @JsonProperty("active_coordinators_7d") long activeCoordinators7d,
            @JsonProperty("active_coordinators_28d") long activeCoordinators28d
    ) {
    }
}
