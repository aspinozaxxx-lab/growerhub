package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class FirmwareDtos {
    private FirmwareDtos() {
    }

    public record TriggerFirmwareUpdateRequest(
            @JsonProperty("version") String version,
            @JsonProperty("firmware_version") String firmwareVersion,
            @JsonProperty("device_id") String deviceId
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FirmwareCheckResponse(
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("current_version") String currentVersion,
            @JsonProperty("hardware_profile") String hardwareProfile,
            @JsonProperty("latest_version") String latestVersion,
            @JsonProperty("target_version") String targetVersion,
            @JsonProperty("firmware_url") String firmwareUrl,
            @JsonProperty("status") String status,
            @JsonProperty("error") String error,
            @JsonProperty("requested_at") java.time.LocalDateTime requestedAt,
            @JsonProperty("completed_at") java.time.LocalDateTime completedAt,
            @JsonProperty("online") Boolean online
    ) {
    }

    public record UploadFirmwareResponse(
            @JsonProperty("result") String result,
            @JsonProperty("version") String version,
            @JsonProperty("path") String path
    ) {
    }

    public record TriggerFirmwareUpdateResponse(
            @JsonProperty("result") String result,
            @JsonProperty("version") String version,
            @JsonProperty("url") String url,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("correlation_id") String correlationId
    ) {
    }

    public record FirmwareVersionResponse(
            @JsonProperty("version") String version,
            @JsonProperty("hardware_profile") String hardwareProfile,
            @JsonProperty("size") Long size,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("mtime") String mtime
    ) {
    }
}
