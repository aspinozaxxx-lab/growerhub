package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class SensorDtos {
    private SensorDtos() {
    }

    public record SensorBindingUpdateRequest(
            @JsonProperty("plant_ids") List<Integer> plantIds
    ) {
    }
}
