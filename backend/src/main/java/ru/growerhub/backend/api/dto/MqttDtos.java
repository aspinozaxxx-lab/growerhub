package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class MqttDtos {
    public record MqttMessageResponse(
            @JsonProperty("id") long id,
            @JsonProperty("received_at") LocalDateTime receivedAt,
            @JsonProperty("direction") String direction,
            @JsonProperty("topic") String topic,
            @JsonProperty("sender") String sender,
            @JsonProperty("kind") String kind,
            @JsonProperty("payload") String payload
    ) {
    }
}
