package ru.growerhub.backend.advisor.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.advisor.contract.WateringAdvice;
import ru.growerhub.backend.advisor.contract.WateringAdviceGateway;
import ru.growerhub.backend.advisor.jpa.AdvisorWateringAdviceEntity;
import ru.growerhub.backend.advisor.jpa.AdvisorWateringAdviceRepository;
import ru.growerhub.backend.common.config.advisor.AdvisorSettings;
import ru.growerhub.backend.common.config.advisor.AdvisorWateringSettings;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;

@Service
public class WateringAdviceEngine {
    private final AdvisorWateringAdviceRepository adviceRepository;
    private final WateringAdviceGateway gateway;
    private final AdvisorSettings advisorSettings;
    private final AdvisorWateringSettings wateringSettings;
    private final ObjectMapper objectMapper;

    public WateringAdviceEngine(
            AdvisorWateringAdviceRepository adviceRepository,
            WateringAdviceGateway gateway,
            AdvisorSettings advisorSettings,
            AdvisorWateringSettings wateringSettings,
            ObjectMapper objectMapper
    ) {
        this.adviceRepository = adviceRepository;
        this.gateway = gateway;
        this.advisorSettings = advisorSettings;
        this.wateringSettings = wateringSettings;
        this.objectMapper = objectMapper;
    }

    public WateringAdvice resolveAdvice(WateringAdviceContext context, LocalDateTime lastWateringEventAt) {
        if (context == null || context.plantId() == null) {
            return null;
        }
        if (!advisorSettings.isEnabled()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AdvisorWateringAdviceEntity cached = adviceRepository.findByPlantId(context.plantId()).orElse(null);
        if (isCacheValid(cached, lastWateringEventAt, now)) {
            return toContract(cached);
        }
        String prompt = buildPrompt(context);
        String response = gateway.requestWateringAdvice(prompt);
        WateringAdvicePayload payload = parsePayload(response);
        if (payload == null) {
            return null;
        }
        AdvisorWateringAdviceEntity entity = cached != null ? cached : AdvisorWateringAdviceEntity.create();
        entity.setPlantId(context.plantId());
        entity.setIsDue(payload.isDue());
        entity.setRecommendedWaterVolumeL(payload.recommendedWaterVolumeL());
        entity.setRecommendedPh(payload.recommendedPh());
        entity.setRecommendedFertilizersPerLiter(payload.recommendedFertilizersPerLiter());
        entity.setCreatedAt(now);
        entity.setValidUntil(now.plus(wateringSettings.getTtl()));
        adviceRepository.save(entity);
        return toContract(entity);
    }

    private boolean isCacheValid(
            AdvisorWateringAdviceEntity cached,
            LocalDateTime lastWateringEventAt,
            LocalDateTime now
    ) {
        if (cached == null) {
            return false;
        }
        if (cached.getValidUntil() == null || now.isAfter(cached.getValidUntil())) {
            return false;
        }
        if (lastWateringEventAt != null && cached.getCreatedAt() != null) {
            return !lastWateringEventAt.isAfter(cached.getCreatedAt());
        }
        return true;
    }

    private WateringAdvice toContract(AdvisorWateringAdviceEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WateringAdvice(
                entity.getIsDue(),
                entity.getRecommendedWaterVolumeL(),
                entity.getRecommendedPh(),
                entity.getRecommendedFertilizersPerLiter(),
                entity.getValidUntil()
        );
    }

    private WateringAdvicePayload parsePayload(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(response, WateringAdvicePayload.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String buildPrompt(WateringAdviceContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plant", buildPlantPayload(context.plant(), context.plantAgeDays()));
        payload.put("last_watering", buildPreviousPayload(context));
        payload.put("history", buildHistoryPayload(context.history()));
        payload.put("fertilizers_name", context.fertilizersName());
        payload.put("fertilizers_format", context.fertilizersFormat());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            json = "{}";
        }
        return """
                You are a watering advisor. Use fertilizers_name and fertilizers_format from input.
                Return ONLY JSON in this schema:
                {
                  "is_due": boolean,
                  "recommended_water_volume_l": number,
                  "recommended_ph": number|null,
                  "recommended_fertilizers_per_liter": string|null
                }
                Input JSON:
                %s
                """.formatted(json);
    }

    private Map<String, Object> buildPlantPayload(PlantInfo plant, Integer ageDays) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plant_type", plant != null ? plant.plantType() : null);
        payload.put("strain", plant != null ? plant.strain() : null);
        payload.put("growth_stage", plant != null ? plant.growthStage() : null);
        payload.put("age_days", ageDays);
        return payload;
    }

    private Map<String, Object> buildPreviousPayload(WateringAdviceContext context) {
        if (context == null || context.previous() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("water_volume_l", context.previous().waterVolumeL());
        payload.put("ph", context.previous().ph());
        payload.put("fertilizers_per_liter", context.previous().fertilizersPerLiter());
        payload.put("event_at", context.previous().eventAt());
        return payload;
    }

    private List<Map<String, Object>> buildHistoryPayload(List<PlantMetricBucketPoint> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .map(point -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("metric_type", point.metricType());
                    item.put("bucket_start", point.bucketStartTs());
                    item.put("value", point.value());
                    return item;
                })
                .toList();
    }

    private record WateringAdvicePayload(
            @JsonProperty("is_due") Boolean isDue,
            @JsonProperty("recommended_water_volume_l") Double recommendedWaterVolumeL,
            @JsonProperty("recommended_ph") Double recommendedPh,
            @JsonProperty("recommended_fertilizers_per_liter") String recommendedFertilizersPerLiter
    ) {
    }
}
