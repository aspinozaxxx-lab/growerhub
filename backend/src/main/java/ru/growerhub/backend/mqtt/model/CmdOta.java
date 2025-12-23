package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CmdOta(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("version") String version,
        @JsonProperty("sha256") String sha256
) {
}
