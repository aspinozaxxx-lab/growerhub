package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import ru.growerhub.backend.common.util.FlexibleLocalDateTimeDeserializer;

public record DeviceServiceEventMessage(
        @JsonProperty("type") String type,
        @JsonProperty("ts") @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class) LocalDateTime ts,
        @JsonProperty("failure_id") String failureId,
        @JsonProperty("sensor_scope") String sensorScope,
        @JsonProperty("sensor_type") String sensorType,
        @JsonProperty("channel") Integer channel,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("auto_reboot") Boolean autoReboot,
        @JsonProperty("errors_count") Integer errorsCount
) {
}
