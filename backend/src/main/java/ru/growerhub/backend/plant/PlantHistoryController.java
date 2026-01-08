package ru.growerhub.backend.plant;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.dto.HistoryDtos;
import ru.growerhub.backend.user.UserEntity;

@RestController
public class PlantHistoryController {
    private static final int MAX_HISTORY_POINTS = 200;

    private final PlantRepository plantRepository;
    private final PlantMetricSampleRepository plantMetricSampleRepository;

    public PlantHistoryController(
            PlantRepository plantRepository,
            PlantMetricSampleRepository plantMetricSampleRepository
    ) {
        this.plantRepository = plantRepository;
        this.plantMetricSampleRepository = plantMetricSampleRepository;
    }

    @GetMapping("/api/plants/{plant_id}/history")
    public List<HistoryDtos.PlantMetricPointResponse> getPlantHistory(
            @PathVariable("plant_id") Integer plantId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @RequestParam(value = "metrics", defaultValue = "SOIL_MOISTURE") String metrics,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        List<PlantMetricType> metricTypes = parseMetricTypes(metrics);
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<PlantMetricSampleEntity> rows = plantMetricSampleRepository
                .findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(plant.getId(), metricTypes, since);
        List<HistoryDtos.PlantMetricPointResponse> payload = new ArrayList<>();
        for (PlantMetricSampleEntity row : downsample(rows, MAX_HISTORY_POINTS)) {
            payload.add(new HistoryDtos.PlantMetricPointResponse(
                    row.getMetricType() != null ? row.getMetricType().name() : null,
                    row.getTs(),
                    row.getValueNumeric()
            ));
        }
        return payload;
    }

    private PlantEntity requireUserPlant(Integer plantId, UserEntity user) {
        PlantEntity plant = plantRepository.findByIdAndUser_Id(plantId, user.getId()).orElse(null);
        if (plant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne naideno");
        }
        return plant;
    }

    private List<PlantMetricType> parseMetricTypes(String metrics) {
        if (metrics == null || metrics.isBlank()) {
            return List.of(PlantMetricType.SOIL_MOISTURE);
        }
        Set<String> parts = Set.of(metrics.split(","));
        List<PlantMetricType> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            try {
                result.add(PlantMetricType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "neizvestnyi metric_type: " + value);
            }
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    private List<PlantMetricSampleEntity> downsample(List<PlantMetricSampleEntity> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<PlantMetricSampleEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }
}
