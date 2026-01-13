package ru.growerhub.backend.sensor.internal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.sensor.SensorBoundPlantView;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.SensorReadingEntity;
import ru.growerhub.backend.sensor.contract.SensorView;

@Service
public class SensorQueryService {
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final SensorPlantBindingRepository bindingRepository;

    public SensorQueryService(
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            SensorPlantBindingRepository bindingRepository
    ) {
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.bindingRepository = bindingRepository;
    }

    public List<SensorView> listByDeviceId(Integer deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        List<SensorEntity> sensors = sensorRepository.findAllByDeviceId(deviceId);
        if (sensors.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<SensorBoundPlantView>> boundPlantsBySensor = loadBindings(sensors);
        List<SensorView> result = new ArrayList<>();
        for (SensorEntity sensor : sensors) {
            SensorReadingEntity last = sensorReadingRepository
                    .findTopBySensor_IdOrderByTsDesc(sensor.getId())
                    .orElse(null);
            List<SensorBoundPlantView> boundPlants = boundPlantsBySensor.getOrDefault(sensor.getId(), List.of());
            result.add(new SensorView(
                    sensor.getId(),
                    sensor.getType(),
                    sensor.getChannel(),
                    sensor.getLabel(),
                    sensor.isDetected(),
                    last != null ? last.getValueNumeric() : null,
                    last != null ? last.getTs() : null,
                    boundPlants
            ));
        }
        return result;
    }

    public List<SensorView> listByPlantId(Integer plantId) {
        if (plantId == null) {
            return List.of();
        }
        List<SensorPlantBindingEntity> bindings = bindingRepository.findAllByPlant_Id(plantId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Integer, SensorEntity> sensors = new HashMap<>();
        PlantEntity plant = null;
        for (SensorPlantBindingEntity binding : bindings) {
            SensorEntity sensor = binding.getSensor();
            if (sensor != null) {
                sensors.putIfAbsent(sensor.getId(), sensor);
            }
            if (plant == null) {
                plant = binding.getPlant();
            }
        }
        if (plant == null) {
            return List.of();
        }
        List<SensorView> result = new ArrayList<>();
        SensorBoundPlantView plantView = toPlantView(plant);
        for (SensorEntity sensor : sensors.values()) {
            SensorReadingEntity last = sensorReadingRepository
                    .findTopBySensor_IdOrderByTsDesc(sensor.getId())
                    .orElse(null);
            result.add(new SensorView(
                    sensor.getId(),
                    sensor.getType(),
                    sensor.getChannel(),
                    sensor.getLabel(),
                    sensor.isDetected(),
                    last != null ? last.getValueNumeric() : null,
                    last != null ? last.getTs() : null,
                    List.of(plantView)
            ));
        }
        return result;
    }

    private Map<Integer, List<SensorBoundPlantView>> loadBindings(List<SensorEntity> sensors) {
        List<Integer> sensorIds = sensors.stream()
                .map(SensorEntity::getId)
                .collect(Collectors.toList());
        Map<Integer, List<SensorBoundPlantView>> boundPlants = new HashMap<>();
        List<SensorPlantBindingEntity> bindings = bindingRepository.findAllBySensor_IdIn(sensorIds);
        for (SensorPlantBindingEntity binding : bindings) {
            SensorEntity sensor = binding.getSensor();
            PlantEntity plant = binding.getPlant();
            if (sensor == null || plant == null) {
                continue;
            }
            boundPlants.computeIfAbsent(sensor.getId(), key -> new ArrayList<>())
                    .add(toPlantView(plant));
        }
        return boundPlants;
    }

    private SensorBoundPlantView toPlantView(PlantEntity plant) {
        LocalDateTime plantedAt = plant.getPlantedAt();
        Integer ageDays = null;
        if (plantedAt != null) {
            LocalDate plantedDate = plantedAt.toLocalDate();
            ageDays = (int) ChronoUnit.DAYS.between(plantedDate, LocalDate.now(ZoneOffset.UTC));
            if (ageDays < 0) {
                ageDays = 0;
            }
        }
        return new SensorBoundPlantView(
                plant.getId(),
                plant.getName(),
                plantedAt,
                plant.getGrowthStage(),
                ageDays
        );
    }
}

