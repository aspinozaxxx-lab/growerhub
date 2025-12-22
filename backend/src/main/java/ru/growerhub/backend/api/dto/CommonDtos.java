package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record MessageResponse(@JsonProperty("message") String message) {
    }

    public record OkResponse(@JsonProperty("ok") Boolean ok) {
    }
}
