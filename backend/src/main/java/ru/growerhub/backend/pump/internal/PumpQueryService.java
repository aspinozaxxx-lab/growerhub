package ru.growerhub.backend.pump.internal;

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
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.pump.PumpBoundPlantView;
import ru.growerhub.backend.pump.PumpEntity;
import ru.growerhub.backend.pump.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.PumpRunningStatusProvider;
import ru.growerhub.backend.pump.contract.PumpView;

@Service
public class PumpQueryService {
    private final PumpService pumpService;
    private final PumpPlantBindingRepository bindingRepository;
    private final PumpRunningStatusProvider runningStatusProvider;
    private final DeviceAccessService deviceAccessService;

    public PumpQueryService(
            PumpService pumpService,
            PumpPlantBindingRepository bindingRepository,
            PumpRunningStatusProvider runningStatusProvider,
            DeviceAccessService deviceAccessService
    ) {
        this.pumpService = pumpService;
        this.bindingRepository = bindingRepository;
        this.runningStatusProvider = runningStatusProvider;
        this.deviceAccessService = deviceAccessService;
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

    public List<PumpView> listByPlant(PlantEntity plant) {
        if (plant == null || plant.getId() == null) {
            return List.of();
        }
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPlant_Id(plant.getId());
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
            nextPlants.add(toPlantView(binding.getPlant(), binding.getRateMlPerHour()));
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
        for (PumpPlantBindingEntity binding : bindings) {
            PumpEntity pump = binding.getPump();
            if (pump == null) {
                continue;
            }
            boundPlants.computeIfAbsent(pump.getId(), key -> new ArrayList<>())
                    .add(toPlantView(binding.getPlant(), binding.getRateMlPerHour()));
        }
        return boundPlants;
    }

    private PumpBoundPlantView toPlantView(PlantEntity plant, Integer rate) {
        if (plant == null) {
            return null;
        }
        Integer ageDays = null;
        if (plant.getPlantedAt() != null) {
            LocalDate plantedDate = plant.getPlantedAt().toLocalDate();
            ageDays = (int) ChronoUnit.DAYS.between(plantedDate, LocalDate.now(ZoneOffset.UTC));
            if (ageDays < 0) {
                ageDays = 0;
            }
        }
        return new PumpBoundPlantView(
                plant.getId(),
                plant.getName(),
                plant.getPlantedAt(),
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
        DeviceSummary summary = deviceAccessService.getDeviceSummary(pump.getDeviceId());
        return summary != null ? summary.deviceId() : null;
    }
}

