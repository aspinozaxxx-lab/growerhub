package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class HistoryDtos {
    private HistoryDtos() {
    }

    public record SensorDataPointResponse(
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("soil_moisture") Double soilMoisture,
            @JsonProperty("air_temperature") Double airTemperature,
            @JsonProperty("air_humidity") Double airHumidity
    ) {
    }

    public record WateringLogResponse(
            @JsonProperty("start_time") LocalDateTime startTime,
            @JsonProperty("duration") Integer duration,
            @JsonProperty("water_used") Double waterUsed,
            @JsonProperty("plant_id") Integer plantId,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter
    ) {
    }
}
