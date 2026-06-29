package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public class ZigbeeDtos {
    public record OverviewResponse(
            @JsonProperty("bridge") BridgeResponse bridge,
            @JsonProperty("coordinator") CoordinatorResponse coordinator,
            @JsonProperty("devices") List<DeviceResponse> devices,
            @JsonProperty("last_command_response") CommandResponse lastCommandResponse
    ) {
    }

    public record BridgeResponse(
            @JsonProperty("base_topic") String baseTopic,
            @JsonProperty("state") String state,
            @JsonProperty("info") Object info,
            @JsonProperty("permit_join") Boolean permitJoin,
            @JsonProperty("permit_join_end") Long permitJoinEnd,
            @JsonProperty("version") String version,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record CoordinatorResponse(
            @JsonProperty("ieee_address") String ieeeAddress,
            @JsonProperty("friendly_name") String friendlyName,
            @JsonProperty("data") Object data
    ) {
    }

    public record DeviceResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("ieee_address") String ieeeAddress,
            @JsonProperty("friendly_name") String friendlyName,
            @JsonProperty("type") String type,
            @JsonProperty("supported") Boolean supported,
            @JsonProperty("disabled") Boolean disabled,
            @JsonProperty("coordinator") boolean coordinator,
            @JsonProperty("bridge_device") Object bridgeDevice,
            @JsonProperty("definition") Object definition,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("features") List<FeatureResponse> features,
            @JsonProperty("metrics") List<FeatureResponse> metrics,
            @JsonProperty("controls") List<FeatureResponse> controls,
            @JsonProperty("state") Object state,
            @JsonProperty("availability") String availability,
            @JsonProperty("last_state_at") LocalDateTime lastStateAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record FeatureResponse(
            @JsonProperty("type") String type,
            @JsonProperty("property") String property,
            @JsonProperty("name") String name,
            @JsonProperty("label") String label,
            @JsonProperty("description") String description,
            @JsonProperty("access") Integer access,
            @JsonProperty("unit") String unit,
            @JsonProperty("values") Object values,
            @JsonProperty("value_min") Object valueMin,
            @JsonProperty("value_max") Object valueMax,
            @JsonProperty("value_step") Object valueStep,
            @JsonProperty("value_on") Object valueOn,
            @JsonProperty("value_off") Object valueOff,
            @JsonProperty("value_toggle") Object valueToggle,
            @JsonProperty("endpoint") String endpoint,
            @JsonProperty("value") Object value
    ) {
    }

    public record CommandResponse(
            @JsonProperty("topic") String topic,
            @JsonProperty("status") String status,
            @JsonProperty("error") String error,
            @JsonProperty("response") Object response,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record CommandPublishResponse(
            @JsonProperty("message") String message,
            @JsonProperty("topic") String topic
    ) {
    }

    public record PermitJoinRequest(@JsonProperty("seconds") Integer seconds) {
    }

    public record SetStateRequest(@JsonProperty("state") String state) {
    }

    public record SetPropertyRequest(
            @JsonProperty("property") String property,
            @JsonProperty("value") Object value
    ) {
    }

    public record RenameRequest(@JsonProperty("friendly_name") String friendlyName) {
    }
}
