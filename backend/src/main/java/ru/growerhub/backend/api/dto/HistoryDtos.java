package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class HistoryDtos {
    private HistoryDtos() {
    }

    public record SensorHistoryPointResponse(
            @JsonProperty("ts") LocalDateTime ts,
            @JsonProperty("value") Double value
    ) {
    }

    public record PlantMetricPointResponse(
            @JsonProperty("metric_type") String metricType,
            @JsonProperty("ts") LocalDateTime ts,
            @JsonProperty("value") Double value
    ) {
    }
}
