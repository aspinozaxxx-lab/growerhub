package ru.growerhub.backend.sensor.internal;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorPlantBindingEntity;

@Service
public class SensorBindingService {
    private final SensorRepository sensorRepository;
    private final SensorPlantBindingRepository bindingRepository;
    private final EntityManager entityManager;

    public SensorBindingService(
            SensorRepository sensorRepository,
            SensorPlantBindingRepository bindingRepository,
            EntityManager entityManager
    ) {
        this.sensorRepository = sensorRepository;
        this.bindingRepository = bindingRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void updateBindings(Integer sensorId, List<Integer> plantIds, AuthenticatedUser user) {
        SensorEntity sensor = sensorRepository.findById(sensorId).orElse(null);
        if (sensor == null) {
            throw new DomainException("not_found", "sensor ne naiden");
        }
        if (!isAdmin(user)) {
            if (sensor.getDevice() == null || sensor.getDevice().getUser() == null) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
            }
            if (!sensor.getDevice().getUser().getId().equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
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

    private PlantEntity resolvePlant(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = entityManager.find(PlantEntity.class, plantId);
        if (plant == null) {
            throw new DomainException("not_found", "rastenie ne naideno");
        }
        if (!isAdmin(user)) {
            if (plant.getUser() == null || !plant.getUser().getId().equals(user.id())) {
                throw new DomainException("forbidden", "rastenie ne naideno");
            }
        }
        return plant;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.isAdmin();
    }
}

