package ru.growerhub.backend.plant.engine;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.SensorType;

@Service
public class PlantHistoryService {
    private final PlantMetricSampleRepository plantMetricSampleRepository;
    private final EntityManager entityManager;
    private final SensorFacade sensorFacade;

    public PlantHistoryService(
            PlantMetricSampleRepository plantMetricSampleRepository,
            EntityManager entityManager,
            SensorFacade sensorFacade
    ) {
        this.plantMetricSampleRepository = plantMetricSampleRepository;
        this.entityManager = entityManager;
        this.sensorFacade = sensorFacade;
    }

    @Transactional
    public void recordFromSensorBindings(List<SensorReadingSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        List<Integer> sensorIds = summaries.stream()
                .map(SensorReadingSummary::sensorId)
                .distinct()
                .collect(Collectors.toList());
        if (sensorIds.isEmpty()) {
            return;
        }
        Map<Integer, List<Integer>> plantIdsBySensor = sensorFacade.getPlantIdsBySensorIds(sensorIds);
        if (plantIdsBySensor.isEmpty()) {
            return;
        }
        List<PlantMetricSampleEntity> samples = new ArrayList<>();
        for (SensorReadingSummary summary : summaries) {
            if (summary == null || summary.value() == null) {
                continue;
            }
            PlantMetricType metricType = mapMetric(summary.type());
            if (metricType == null) {
                continue;
            }
            List<Integer> plantIds = plantIdsBySensor.get(summary.sensorId());
            if (plantIds == null || plantIds.isEmpty()) {
                continue;
            }
            for (Integer plantId : plantIds) {
                PlantEntity plant = entityManager.find(PlantEntity.class, plantId);
                if (plant == null) {
                    continue;
                }
                PlantMetricSampleEntity sample = PlantMetricSampleEntity.create();
                sample.setPlant(plant);
                sample.setMetricType(metricType);
                sample.setTs(summary.ts());
                sample.setValueNumeric(summary.value());
                sample.setCreatedAt(LocalDateTime.now());
                samples.add(sample);
            }
        }
        if (!samples.isEmpty()) {
            plantMetricSampleRepository.saveAll(samples);
        }
    }

    @Transactional
    public void recordWateringEvent(PlantEntity plant, double volumeL, LocalDateTime eventAt) {
        if (plant == null) {
            return;
        }
        PlantMetricSampleEntity sample = PlantMetricSampleEntity.create();
        sample.setPlant(plant);
        sample.setMetricType(PlantMetricType.WATERING_VOLUME_L);
        sample.setTs(eventAt);
        sample.setValueNumeric(volumeL);
        sample.setCreatedAt(LocalDateTime.now());
        plantMetricSampleRepository.save(sample);
    }

    private PlantMetricType mapMetric(SensorType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SOIL_MOISTURE -> PlantMetricType.SOIL_MOISTURE;
            case AIR_TEMPERATURE -> PlantMetricType.AIR_TEMPERATURE;
            case AIR_HUMIDITY -> PlantMetricType.AIR_HUMIDITY;
        };
    }
}



