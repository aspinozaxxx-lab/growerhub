package ru.growerhub.backend.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApiValidationError(
        @JsonProperty("detail") List<ApiValidationErrorItem> detail
) {}
