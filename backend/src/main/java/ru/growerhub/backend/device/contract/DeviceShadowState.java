package ru.growerhub.backend.device.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import java.util.List;
import ru.growerhub.backend.common.util.FlexibleLocalDateTimeDeserializer;

public record DeviceShadowState(
        @JsonProperty("manual_watering") ManualWateringState manualWatering,
        @JsonProperty("fw_ver") String fwVer,
        @JsonProperty("soil_moisture") Double soilMoisture,
        @JsonProperty("air_temperature") Double airTemperature,
        @JsonProperty("air_humidity") Double airHumidity,
        @JsonProperty("air") AirState air,
        @JsonProperty("soil") SoilState soil,
        @JsonProperty("light") RelayState light,
        @JsonProperty("pump") RelayState pump,
        @JsonProperty("scenarios") ScenariosState scenarios
) {
    public record ManualWateringState(
            @JsonProperty("status") String status,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("started_at") @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
            LocalDateTime startedAt,
            @JsonProperty("remaining_s") Integer remainingS,
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter,
            @JsonProperty("journal_written_for_correlation_id") String journalWrittenForCorrelationId
    ) {
    }

    public record AirState(
            @JsonProperty("available") Boolean available,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("humidity") Double humidity
    ) {
    }

    public record SoilState(
            @JsonProperty("ports") List<SoilPort> ports
    ) {
    }

    public record SoilPort(
            @JsonProperty("port") Integer port,
            @JsonProperty("detected") Boolean detected,
            @JsonProperty("percent") Integer percent
    ) {
    }

    public record RelayState(
            @JsonProperty("status") String status
    ) {
    }

    public record ScenarioState(
            @JsonProperty("enabled") Boolean enabled
    ) {
    }

    public record ScenariosState(
            @JsonProperty("water_time") ScenarioState waterTime,
            @JsonProperty("water_moisture") ScenarioState waterMoisture,
            @JsonProperty("light_schedule") ScenarioState lightSchedule
    ) {
    }
}
