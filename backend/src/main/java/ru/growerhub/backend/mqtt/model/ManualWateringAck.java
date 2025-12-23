package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ManualWateringAck(
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("result") String result,
        @JsonProperty("reason") String reason,
        @JsonProperty("status") String status
) {
}
