package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import ru.growerhub.backend.common.FlexibleLocalDateTimeDeserializer;

public record ManualWateringState(
        @JsonProperty("status") String status,
        @JsonProperty("duration_s") Integer durationS,
        @JsonProperty("started_at") @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class) LocalDateTime startedAt,
        @JsonProperty("remaining_s") Integer remainingS,
        @JsonProperty("correlation_id") String correlationId
) {
}
