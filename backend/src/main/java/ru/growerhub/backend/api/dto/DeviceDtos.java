package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceStatusRequest(
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("soil_moisture") Double soilMoisture,
            @JsonProperty("air_temperature") Double airTemperature,
            @JsonProperty("air_humidity") Double airHumidity,
            @JsonProperty("is_watering") Boolean isWatering,
            @JsonProperty("is_light_on") Boolean isLightOn,
            @JsonProperty("last_watering") LocalDateTime lastWatering
    ) {
    }

    public record DeviceSettingsRequest(
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("watering_speed_lph") Double wateringSpeedLph,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration
    ) {
    }

    public record DeviceSettingsResponse(
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("watering_speed_lph") Double wateringSpeedLph,
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

    public record DeviceResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("soil_moisture") Double soilMoisture,
            @JsonProperty("air_temperature") Double airTemperature,
            @JsonProperty("air_humidity") Double airHumidity,
            @JsonProperty("is_watering") Boolean isWatering,
            @JsonProperty("is_light_on") Boolean isLightOn,
            @JsonProperty("last_watering") LocalDateTime lastWatering,
            @JsonProperty("last_seen") LocalDateTime lastSeen,
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("watering_speed_lph") Double wateringSpeedLph,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration,
            @JsonProperty("current_version") String currentVersion,
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("firmware_version") String firmwareVersion,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("plant_ids") List<Integer> plantIds
    ) {
    }

    public record AdminDeviceResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("soil_moisture") Double soilMoisture,
            @JsonProperty("air_temperature") Double airTemperature,
            @JsonProperty("air_humidity") Double airHumidity,
            @JsonProperty("is_watering") Boolean isWatering,
            @JsonProperty("is_light_on") Boolean isLightOn,
            @JsonProperty("last_watering") LocalDateTime lastWatering,
            @JsonProperty("last_seen") LocalDateTime lastSeen,
            @JsonProperty("target_moisture") Double targetMoisture,
            @JsonProperty("watering_duration") Integer wateringDuration,
            @JsonProperty("watering_timeout") Integer wateringTimeout,
            @JsonProperty("watering_speed_lph") Double wateringSpeedLph,
            @JsonProperty("light_on_hour") Integer lightOnHour,
            @JsonProperty("light_off_hour") Integer lightOffHour,
            @JsonProperty("light_duration") Integer lightDuration,
            @JsonProperty("current_version") String currentVersion,
            @JsonProperty("update_available") Boolean updateAvailable,
            @JsonProperty("firmware_version") String firmwareVersion,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("plant_ids") List<Integer> plantIds,
            @JsonProperty("owner") DeviceOwnerInfoResponse owner
    ) {
    }

    public record AssignToMeRequest(@JsonProperty("device_id") Integer deviceId) {
    }

    public record AdminAssignRequest(@JsonProperty("user_id") Integer userId) {
    }
}
