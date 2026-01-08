package ru.growerhub.backend.plant;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.sensor.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.SensorPlantBindingRepository;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.SensorType;

@Service
public class PlantHistoryService {
    private final PlantMetricSampleRepository plantMetricSampleRepository;
    private final SensorPlantBindingRepository bindingRepository;

    public PlantHistoryService(
            PlantMetricSampleRepository plantMetricSampleRepository,
            SensorPlantBindingRepository bindingRepository
    ) {
        this.plantMetricSampleRepository = plantMetricSampleRepository;
        this.bindingRepository = bindingRepository;
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
        List<SensorPlantBindingEntity> bindings = bindingRepository.findAllBySensor_IdIn(sensorIds);
        if (bindings.isEmpty()) {
            return;
        }
        Map<Integer, List<PlantEntity>> plantsBySensor = new HashMap<>();
        for (SensorPlantBindingEntity binding : bindings) {
            if (binding.getSensor() == null || binding.getPlant() == null) {
                continue;
            }
            plantsBySensor.computeIfAbsent(binding.getSensor().getId(), key -> new ArrayList<>())
                    .add(binding.getPlant());
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
            List<PlantEntity> plants = plantsBySensor.get(summary.sensorId());
            if (plants == null || plants.isEmpty()) {
                continue;
            }
            for (PlantEntity plant : plants) {
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
