package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ManualWateringDtos {
    private ManualWateringDtos() {
    }

    public record ManualWateringStartRequest(
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter
    ) {
    }

    public record ManualWateringStartResponse(
            @JsonProperty("correlation_id") String correlationId
    ) {
    }

    public record ManualWateringStopRequest(
            @JsonProperty("device_id") String deviceId
    ) {
    }

    public record ManualWateringStopResponse(
            @JsonProperty("correlation_id") String correlationId
    ) {
    }

    public record ManualWateringRebootRequest(
            @JsonProperty("device_id") String deviceId
    ) {
    }

    public record ManualWateringRebootResponse(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("message") String message
    ) {
    }

    public record ManualWateringStatusResponse(
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

    public record ManualWateringAckResponse(
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("result") String result,
            @JsonProperty("reason") String reason,
            @JsonProperty("status") String status
    ) {
    }

    public record ShadowStateRequest(
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("state") String state // TODO: map structured JSON state.
    ) {
    }

    public record DebugManualWateringConfigResponse(
            @JsonProperty("mqtt_host") String mqttHost,
            @JsonProperty("mqtt_port") Integer mqttPort,
            @JsonProperty("mqtt_username") String mqttUsername,
            @JsonProperty("mqtt_tls") Boolean mqttTls,
            @JsonProperty("debug") Boolean debug
    ) {
    }

    public record DebugManualWateringSnapshotResponse(
            @JsonProperty("raw") String raw, // TODO: map structured JSON snapshot.
            @JsonProperty("view") String view // TODO: map structured JSON snapshot.
    ) {
    }
}
