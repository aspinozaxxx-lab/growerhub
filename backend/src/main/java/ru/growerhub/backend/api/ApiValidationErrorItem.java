package ru.growerhub.backend.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApiValidationErrorItem(
        @JsonProperty("loc") List<String> loc,
        @JsonProperty("msg") String msg,
        @JsonProperty("type") String type
) {}
