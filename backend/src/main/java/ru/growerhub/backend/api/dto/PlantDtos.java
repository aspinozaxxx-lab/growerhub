package ru.growerhub.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import ru.growerhub.backend.api.dto.DeviceDtos.PumpResponse;
import ru.growerhub.backend.api.dto.DeviceDtos.SensorResponse;
import ru.growerhub.backend.common.util.FlexibleLocalDateTimeDeserializer;

public final class PlantDtos {
    private PlantDtos() {
    }

    public record PlantGroupCreateRequest(
            @NotNull
            @JsonProperty("name") String name
    ) {
    }

    public record PlantGroupUpdateRequest(
            @NotNull
            @JsonProperty("name") String name
    ) {
    }

    public record PlantGroupResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("user_id") Integer userId
    ) {
    }

    public record PlantCreateRequest(
            @NotNull
            @JsonProperty("name") String name,
            @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("plant_group_id") Integer plantGroupId,
            @JsonProperty("plant_type") String plantType,
            @JsonProperty("strain") String strain,
            @JsonProperty("growth_stage") String growthStage
    ) {
    }

    public record PlantUpdateRequest(
            @JsonProperty("name") String name,
            @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("plant_group_id") Integer plantGroupId,
            @JsonProperty("plant_type") String plantType,
            @JsonProperty("strain") String strain,
            @JsonProperty("growth_stage") String growthStage
    ) {
    }

    public record PlantHarvestRequest(
            @JsonProperty("text") String text,
            @NotNull
            @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
            @JsonProperty("harvested_at") LocalDateTime harvestedAt
    ) {
    }

    public record PlantResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("harvested_at") LocalDateTime harvestedAt,
            @JsonProperty("plant_type") String plantType,
            @JsonProperty("strain") String strain,
            @JsonProperty("growth_stage") String growthStage,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("plant_group") PlantGroupResponse plantGroup,
            @JsonProperty("sensors") List<SensorResponse> sensors,
            @JsonProperty("pumps") List<PumpResponse> pumps,
            @JsonProperty("watering_previous") PlantWateringPreviousResponse wateringPrevious,
            @JsonProperty("watering_advice") PlantWateringAdviceResponse wateringAdvice
    ) {
    }

    public record PlantListResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("planted_at") LocalDateTime plantedAt,
            @JsonProperty("harvested_at") LocalDateTime harvestedAt,
            @JsonProperty("plant_type") String plantType,
            @JsonProperty("strain") String strain,
            @JsonProperty("growth_stage") String growthStage,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("plant_group") PlantGroupResponse plantGroup,
            @JsonProperty("sensors") List<PlantListSensorResponse> sensors,
            @JsonProperty("pumps") List<PlantListPumpResponse> pumps
    ) {
    }

    public record PlantListSensorResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("type") String type,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("detected") Boolean detected,
            @JsonProperty("last_value") Double lastValue,
            @JsonProperty("last_ts") LocalDateTime lastTs
    ) {
    }

    public record PlantListPumpResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("is_running") Boolean isRunning
    ) {
    }

    public record PlantWateringPreviousResponse(
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter,
            @JsonProperty("event_at") LocalDateTime eventAt
    ) {
    }

    public record PlantWateringAdviceResponse(
            @JsonProperty("is_due") Boolean isDue,
            @JsonProperty("recommended_water_volume_l") Double recommendedWaterVolumeL,
            @JsonProperty("recommended_ph") Double recommendedPh,
            @JsonProperty("recommended_fertilizers_per_liter") String recommendedFertilizersPerLiter,
            @JsonProperty("valid_until") LocalDateTime validUntil
    ) {
    }

    public record PlantJournalPhotoResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("url") String url,
            @JsonProperty("caption") String caption,
            @JsonProperty("has_data") Boolean hasData
    ) {
    }

    public record PlantJournalWateringDetailsResponse(
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter
    ) {
    }

    public record PlantJournalEntryCreateRequest(
            @NotNull
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
            @JsonProperty("event_at") LocalDateTime eventAt,
            @JsonProperty("photo_urls") List<String> photoUrls
    ) {
    }

    public record PlantJournalEntryUpdateRequest(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {
    }

    public record PlantJournalEntryResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("plant_id") Integer plantId,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonProperty("event_at") LocalDateTime eventAt,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("photos") List<PlantJournalPhotoResponse> photos,
            @JsonProperty("watering_details") PlantJournalWateringDetailsResponse wateringDetails
    ) {
    }

    public record AdminPlantResponse(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("owner_email") String ownerEmail,
            @JsonProperty("owner_username") String ownerUsername,
            @JsonProperty("owner_id") Integer ownerId,
            @JsonProperty("group_name") String groupName
    ) {
    }
}
