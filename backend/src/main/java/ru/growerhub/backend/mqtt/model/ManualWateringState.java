package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ManualWateringState(
        @JsonProperty("status") String status,
        @JsonProperty("duration_s") Integer durationS,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("remaining_s") Integer remainingS,
        @JsonProperty("correlation_id") String correlationId
) {
}
