package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PumpDtos {
    private PumpDtos() {
    }

    public record PumpBindingItem(
            @NotNull
            @JsonProperty("plant_id") Integer plantId,
            @JsonProperty("rate_ml_per_hour") Integer rateMlPerHour
    ) {
    }

    public record PumpBindingUpdateRequest(
            @JsonProperty("items") List<PumpBindingItem> items
    ) {
    }

    public record PumpWateringStartRequest(
            @Min(1)
            @Max(3600)
            @JsonProperty("duration_s") Integer durationS,
            @DecimalMin(value = "0.0", inclusive = false)
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter
    ) {
    }

    public record PumpWateringStartResponse(
            @JsonProperty("correlation_id") String correlationId
    ) {
    }

    public record PumpWateringStopResponse(
            @JsonProperty("correlation_id") String correlationId
    ) {
    }

    public record PumpWateringRebootResponse(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("message") String message
    ) {
    }

    public record PumpWateringStatusResponse(
            @JsonProperty("status") String status,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("duration") Integer duration,
            @JsonProperty("started_at") String startedAt,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("remaining_s") Integer remainingS,
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("last_seen_at") String lastSeenAt,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("offline_reason") String offlineReason,
            @JsonProperty("source") String source
    ) {
    }

    public record PumpWateringAckResponse(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("result") String result,
            @JsonProperty("reason") String reason,
            @JsonProperty("status") String status
    ) {
    }

    public record PumpWateringAckWaitRequest(
            @NotNull
            @Size(min = 1)
            @JsonProperty("correlation_id") String correlationId
    ) {
    }
}
