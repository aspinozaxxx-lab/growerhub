package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CmdReboot(
        @JsonProperty("type") String type,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("issued_at") Long issuedAt
) {
}
