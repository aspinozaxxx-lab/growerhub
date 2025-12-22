package ru.growerhub.backend.api.dto;

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

    public record FirmwareCheckResponse(
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("latest_version") String latestVersion,
            @JsonProperty("firmware_url") String firmwareUrl
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
            @JsonProperty("sha256") String sha256
    ) {
    }

    public record FirmwareVersionResponse(
            @JsonProperty("version") String version,
            @JsonProperty("size") Long size,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("mtime") String mtime
    ) {
    }
}
