package ru.growerhub.backend.pump.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.pump.contract.PumpBoundPlantView;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.contract.PumpRunningStatusProvider;
import ru.growerhub.backend.pump.contract.PumpView;

@Service
public class PumpQueryService {
    private final PumpService pumpService;
    private final PumpPlantBindingRepository bindingRepository;
    private final PumpRunningStatusProvider runningStatusProvider;
    private final DeviceFacade deviceFacade;
    private final PlantFacade plantFacade;

    public PumpQueryService(
            PumpService pumpService,
            PumpPlantBindingRepository bindingRepository,
            PumpRunningStatusProvider runningStatusProvider,
            @Lazy DeviceFacade deviceFacade,
            PlantFacade plantFacade
    ) {
        this.pumpService = pumpService;
        this.bindingRepository = bindingRepository;
        this.runningStatusProvider = runningStatusProvider;
        this.deviceFacade = deviceFacade;
        this.plantFacade = plantFacade;
    }

    public List<PumpView> listByDevice(Integer deviceId, DeviceShadowState state) {
        List<PumpEntity> pumps = pumpService.listByDevice(deviceId, true);
        if (pumps.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<PumpBoundPlantView>> boundPlantsByPump = loadBindings(pumps);
        List<PumpView> result = new ArrayList<>();
        for (PumpEntity pump : pumps) {
            Boolean isRunning = resolveIsRunning(pump, state);
            List<PumpBoundPlantView> boundPlants = boundPlantsByPump.getOrDefault(pump.getId(), List.of());
            result.add(new PumpView(
                    pump.getId(),
                    pump.getChannel(),
                    pump.getLabel(),
                    isRunning,
                    boundPlants
            ));
        }
        return result;
    }

    public List<PumpView> listByPlantId(Integer plantId) {
        if (plantId == null) {
            return List.of();
        }
        PlantInfo plant = plantFacade.getPlantInfoById(plantId);
        if (plant == null) {
            return List.of();
        }
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPlantId(plantId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Integer, PumpView> byPump = new HashMap<>();
        for (PumpPlantBindingEntity binding : bindings) {
            PumpEntity pump = binding.getPump();
            if (pump == null || pump.getId() == null) {
                continue;
            }
            Boolean isRunning = runningStatusProvider.isPumpRunning(
                    resolveDeviceId(pump),
                    pump.getChannel() != null ? pump.getChannel() : 0
            );
            PumpView existing = byPump.get(pump.getId());
            List<PumpBoundPlantView> nextPlants = new ArrayList<>();
            if (existing != null && existing.boundPlants() != null) {
                nextPlants.addAll(existing.boundPlants());
            }
            nextPlants.add(toPlantView(plant, binding.getRateMlPerHour()));
            byPump.put(pump.getId(), new PumpView(
                    pump.getId(),
                    pump.getChannel(),
                    pump.getLabel(),
                    isRunning,
                    nextPlants
            ));
        }
        return new ArrayList<>(byPump.values());
    }

    private Map<Integer, List<PumpBoundPlantView>> loadBindings(List<PumpEntity> pumps) {
        List<Integer> pumpIds = pumps.stream()
                .map(PumpEntity::getId)
                .collect(Collectors.toList());
        Map<Integer, List<PumpBoundPlantView>> boundPlants = new HashMap<>();
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPump_IdIn(pumpIds);
        Map<Integer, PlantInfo> plantCache = new HashMap<>();
        for (PumpPlantBindingEntity binding : bindings) {
            PumpEntity pump = binding.getPump();
            if (pump == null) {
                continue;
            }
            Integer plantId = binding.getPlantId();
            if (plantId == null) {
                continue;
            }
            PlantInfo plant = plantCache.computeIfAbsent(plantId, plantFacade::getPlantInfoById);
            if (plant == null) {
                continue;
            }
            boundPlants.computeIfAbsent(pump.getId(), key -> new ArrayList<>())
                    .add(toPlantView(plant, binding.getRateMlPerHour()));
        }
        return boundPlants;
    }

    private PumpBoundPlantView toPlantView(PlantInfo plant, Integer rate) {
        if (plant == null) {
            return null;
        }
        Integer ageDays = null;
        if (plant.plantedAt() != null) {
            LocalDate plantedDate = plant.plantedAt().toLocalDate();
            ageDays = (int) ChronoUnit.DAYS.between(plantedDate, LocalDate.now(ZoneOffset.UTC));
            if (ageDays < 0) {
                ageDays = 0;
            }
        }
        return new PumpBoundPlantView(
                plant.id(),
                plant.name(),
                plant.plantedAt(),
                ageDays,
                rate
        );
    }

    private Boolean resolveIsRunning(PumpEntity pump, DeviceShadowState state) {
        if (pump == null || state == null) {
            return null;
        }
        if (pump.getChannel() != null && pump.getChannel() != 0) {
            return null;
        }
        if (state.manualWatering() != null && state.manualWatering().status() != null) {
            return "running".equals(state.manualWatering().status());
        }
        DeviceShadowState.RelayState relay = state.pump();
        if (relay != null && relay.status() != null) {
            return "on".equalsIgnoreCase(relay.status());
        }
        return null;
    }

    private String resolveDeviceId(PumpEntity pump) {
        if (pump == null || pump.getDeviceId() == null) {
            return null;
        }
        DeviceSummary summary = deviceFacade.getDeviceSummary(pump.getDeviceId());
        return summary != null ? summary.deviceId() : null;
    }
}



