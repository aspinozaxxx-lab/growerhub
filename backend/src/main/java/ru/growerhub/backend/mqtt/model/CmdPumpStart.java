package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record CmdPumpStart(
        @JsonProperty("type") String type,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("ts") LocalDateTime ts,
        @JsonProperty("duration_s") Integer durationS
) {
}
