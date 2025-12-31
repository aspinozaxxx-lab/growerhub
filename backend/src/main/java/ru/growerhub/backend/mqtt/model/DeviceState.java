package ru.growerhub.backend.mqtt.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DeviceState(
        @JsonProperty("manual_watering") ManualWateringState manualWatering,
        @JsonProperty("fw") String fw,
        @JsonProperty("fw_ver") String fwVer,
        @JsonProperty("fw_name") String fwName,
        @JsonProperty("fw_build") String fwBuild,
        @JsonProperty("soil_moisture") Double soilMoisture,
        @JsonProperty("air_temperature") Double airTemperature,
        @JsonProperty("air_humidity") Double airHumidity,
        @JsonProperty("air") AirState air,
        @JsonProperty("soil") SoilState soil,
        @JsonProperty("light") RelayState light,
        @JsonProperty("pump") RelayState pump,
        @JsonProperty("scenarios") ScenariosState scenarios
) {
    // Payload dlya bloka vozduha (DHT22).
    public record AirState(
            @JsonProperty("available") Boolean available,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("humidity") Double humidity
    ) {
    }

    // Payload dlya pochvennyh datchikov.
    public record SoilState(
            @JsonProperty("ports") List<SoilPort> ports
    ) {
    }

    // Obyavlenie odnogo porta pochvennogo datchika.
    public record SoilPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("detected") Boolean detected,
            @JsonProperty("percent") Integer percent
    ) {
    }

    // Payload statusa rele.
    public record RelayState(
            @JsonProperty("status") String status
    ) {
    }

    // Payload dlya scenariya s flagom enabled.
    public record ScenarioState(
            @JsonProperty("enabled") Boolean enabled
    ) {
    }

    // Payload dlya vsekh scenariev.
    public record ScenariosState(
            @JsonProperty("water_time") ScenarioState waterTime,
            @JsonProperty("water_moisture") ScenarioState waterMoisture,
            @JsonProperty("light_schedule") ScenarioState lightSchedule
    ) {
    }
}
