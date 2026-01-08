package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceStatusRequest(
            @NotNull
            @JsonProperty("device_id") String deviceId,
            @NotNull
            @JsonProperty("soil_moisture") Double soilMoisture,
            @NotNull
            @JsonProperty("air_temperature") Double airTemperature,
            @NotNull
            @JsonProperty("air_humidity") Double airHumidity,
            @NotNull
            @JsonProperty("is_watering") Boolean isWatering,
            @NotNull
            @JsonProperty("is_light_on") Boolean isLightOn,
            @JsonProperty("last_watering") LocalDateTime lastWatering
    ) {
    }

    public record DeviceSettingsRequest(
            @NotNull
            @JsonProperty("target_moisture") Double targetMoisture,
            @NotNull
            @JsonProperty("watering_duration") Integer wateringDuration,
            @NotNull
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @NotNull
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @NotNull
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @NotNull
            @JsonProperty("light_duration") Integer lightDuration
    ) {
    }

    public record DeviceSettingsResponse(
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration,
            @JsonProperty("update_available") Boolean updateAvailable
    ) {
    }

    public record DeviceOwnerInfoResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("email") String email,
            @JsonProperty("username") String username
    ) {
    }

    public record BoundPlantResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("growth_stage") String growthStage,
            @JsonProperty("age_days") Integer ageDays
    ) {
    }

    public record SensorResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("type") String type,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("detected") Boolean detected,
            @JsonProperty("last_value") Double lastValue,
            @JsonProperty("last_ts") LocalDateTime lastTs,
            @JsonProperty("bound_plants") List<BoundPlantResponse> boundPlants
    ) {
    }

    public record PumpBoundPlantResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("age_days") Integer ageDays,
            @JsonProperty("rate_ml_per_hour") Integer rateMlPerHour
    ) {
    }

    public record PumpResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("is_running") Boolean isRunning,
            @JsonProperty("bound_plants") List<PumpBoundPlantResponse> boundPlants
    ) {
    }

    public record DeviceResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("last_seen") LocalDateTime lastSeen,
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration,
            @JsonProperty("current_version") String currentVersion,
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("firmware_version") String firmwareVersion,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("sensors") List<SensorResponse> sensors,
            @JsonProperty("pumps") List<PumpResponse> pumps
    ) {
    }

    public record AdminDeviceResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("last_seen") LocalDateTime lastSeen,
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration,
            @JsonProperty("current_version") String currentVersion,
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("firmware_version") String firmwareVersion,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("sensors") List<SensorResponse> sensors,
            @JsonProperty("pumps") List<PumpResponse> pumps,
            @JsonProperty("owner") DeviceOwnerInfoResponse owner
    ) {
    }

    public record AssignToMeRequest(
            @NotNull
            @JsonProperty("device_id") Integer deviceId
    ) {
    }

    public record AdminAssignRequest(
            @NotNull
            @JsonProperty("user_id") Integer userId
    ) {
    }
}
