package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceState(
        @JsonProperty("manual_watering") ManualWateringState manualWatering,
        @JsonProperty("fw") String fw,
        @JsonProperty("fw_ver") String fwVer,
        @JsonProperty("fw_name") String fwName,
        @JsonProperty("fw_build") String fwBuild,
        @JsonProperty("soil_moisture") Double soilMoisture,
        @JsonProperty("air_temperature") Double airTemperature,
        @JsonProperty("air_humidity") Double airHumidity
) {
}
