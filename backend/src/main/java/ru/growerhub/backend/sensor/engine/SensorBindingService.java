package ru.growerhub.backend.sensor.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingEntity;
import ru.growerhub.backend.sensor.jpa.SensorPlantBindingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;

@Service
public class SensorBindingService {
    private final SensorRepository sensorRepository;
    private final SensorPlantBindingRepository bindingRepository;
    private final PlantFacade plantFacade;
    private final DeviceAccessService deviceAccessService;

    public SensorBindingService(
            SensorRepository sensorRepository,
            SensorPlantBindingRepository bindingRepository,
            @Lazy PlantFacade plantFacade,
            DeviceAccessService deviceAccessService
    ) {
        this.sensorRepository = sensorRepository;
        this.bindingRepository = bindingRepository;
        this.plantFacade = plantFacade;
        this.deviceAccessService = deviceAccessService;
    }

    @Transactional
    public void updateBindings(Integer sensorId, List<Integer> plantIds, AuthenticatedUser user) {
        SensorEntity sensor = sensorRepository.findById(sensorId).orElse(null);
        if (sensor == null) {
            throw new DomainException("not_found", "sensor ne naiden");
        }
        if (!isAdmin(user)) {
            DeviceSummary summary = deviceAccessService.getDeviceSummary(sensor.getDeviceId());
            Integer ownerId = summary != null ? summary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
            }
        }

        Set<Integer> nextIds = plantIds != null ? new HashSet<>(plantIds) : Set.of();
        Set<Integer> currentIds = new HashSet<>();
        List<SensorPlantBindingEntity> current = bindingRepository.findAllBySensor_Id(sensorId);
        for (SensorPlantBindingEntity binding : current) {
            Integer plantId = binding.getPlantId();
            if (plantId != null) {
                currentIds.add(plantId);
            }
        }

        for (SensorPlantBindingEntity binding : current) {
            Integer plantId = binding.getPlantId();
            if (plantId == null) {
                continue;
            }
            if (!nextIds.contains(plantId)) {
                bindingRepository.delete(binding);
            }
        }

        for (Integer plantId : nextIds) {
            if (currentIds.contains(plantId)) {
                continue;
            }
            plantFacade.requireOwnedPlantInfo(plantId, user);
            SensorPlantBindingEntity binding = SensorPlantBindingEntity.create();
            binding.setSensor(sensor);
            binding.setPlantId(plantId);
            bindingRepository.save(binding);
        }
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<Integer>> getPlantIdsBySensorIds(List<Integer> sensorIds) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            return Map.of();
        }
        List<SensorPlantBindingEntity> bindings = bindingRepository.findAllBySensor_IdIn(sensorIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (SensorPlantBindingEntity binding : bindings) {
            if (binding.getSensor() == null || binding.getPlantId() == null) {
                continue;
            }
            Integer sensorId = binding.getSensor().getId();
            if (sensorId == null) {
                continue;
            }
            result.computeIfAbsent(sensorId, key -> new ArrayList<>()).add(binding.getPlantId());
        }
        return result;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.isAdmin();
    }
}



