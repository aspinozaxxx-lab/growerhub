package ru.growerhub.backend.sensor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantRepository;
import ru.growerhub.backend.user.UserEntity;

@Service
public class SensorBindingService {
    private final SensorRepository sensorRepository;
    private final SensorPlantBindingRepository bindingRepository;
    private final PlantRepository plantRepository;

    public SensorBindingService(
            SensorRepository sensorRepository,
            SensorPlantBindingRepository bindingRepository,
            PlantRepository plantRepository
    ) {
        this.sensorRepository = sensorRepository;
        this.bindingRepository = bindingRepository;
        this.plantRepository = plantRepository;
    }

    @Transactional
    public void updateBindings(Integer sensorId, List<Integer> plantIds, UserEntity user) {
        SensorEntity sensor = sensorRepository.findById(sensorId).orElse(null);
        if (sensor == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "sensor ne naiden");
        }
        if (!isAdmin(user)) {
            if (sensor.getDevice() == null || sensor.getDevice().getUser() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo sensora");
            }
            if (!sensor.getDevice().getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo sensora");
            }
        }

        Set<Integer> nextIds = plantIds != null ? new HashSet<>(plantIds) : Set.of();
        Set<Integer> currentIds = new HashSet<>();
        List<SensorPlantBindingEntity> current = bindingRepository.findAllBySensor_Id(sensorId);
        for (SensorPlantBindingEntity binding : current) {
            PlantEntity plant = binding.getPlant();
            if (plant != null) {
                currentIds.add(plant.getId());
            }
        }

        for (SensorPlantBindingEntity binding : current) {
            PlantEntity plant = binding.getPlant();
            if (plant == null) {
                continue;
            }
            if (!nextIds.contains(plant.getId())) {
                bindingRepository.delete(binding);
            }
        }

        for (Integer plantId : nextIds) {
            if (currentIds.contains(plantId)) {
                continue;
            }
            PlantEntity plant = resolvePlant(plantId, user);
            SensorPlantBindingEntity binding = SensorPlantBindingEntity.create();
            binding.setSensor(sensor);
            binding.setPlant(plant);
            bindingRepository.save(binding);
        }
    }

    private PlantEntity resolvePlant(Integer plantId, UserEntity user) {
        PlantEntity plant;
        if (isAdmin(user)) {
            plant = plantRepository.findById(plantId).orElse(null);
        } else {
            plant = plantRepository.findByIdAndUser_Id(plantId, user.getId()).orElse(null);
        }
        if (plant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne naideno");
        }
        return plant;
    }

    private boolean isAdmin(UserEntity user) {
        return user != null && "admin".equals(user.getRole());
    }
}
