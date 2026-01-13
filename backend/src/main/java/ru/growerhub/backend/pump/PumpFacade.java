package ru.growerhub.backend.pump;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.pump.internal.PumpBindingService;
import ru.growerhub.backend.pump.internal.PumpQueryService;
import ru.growerhub.backend.pump.internal.PumpWateringService;
import ru.growerhub.backend.pump.contract.PumpView;

@Service
public class PumpFacade {
    private final PumpBindingService bindingService;
    private final PumpWateringService wateringService;
    private final PumpQueryService queryService;
    private final EntityManager entityManager;

    public PumpFacade(
            PumpBindingService bindingService,
            PumpWateringService wateringService,
            PumpQueryService queryService,
            EntityManager entityManager
    ) {
        this.bindingService = bindingService;
        this.wateringService = wateringService;
        this.queryService = queryService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user) {
        List<ru.growerhub.backend.pump.internal.PumpBindingService.PumpBindingItem> mapped = items != null
                ? items.stream()
                .map(item -> new ru.growerhub.backend.pump.internal.PumpBindingService.PumpBindingItem(
                        item.plantId(),
                        item.rateMlPerHour()
                ))
                .toList()
                : null;
        bindingService.updateBindings(pumpId, mapped, user);
    }

    @Transactional
    public PumpWateringService.PumpStartResult start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user) {
        return wateringService.start(
                pumpId,
                new PumpWateringService.PumpWateringRequest(
                        request.durationS(),
                        request.waterVolumeL(),
                        request.ph(),
                        request.fertilizersPerLiter()
                ),
                user
        );
    }

    @Transactional
    public PumpWateringService.PumpStopResult stop(Integer pumpId, AuthenticatedUser user) {
        return wateringService.stop(pumpId, user);
    }

    @Transactional
    public PumpWateringService.PumpRebootResult reboot(Integer pumpId, AuthenticatedUser user) {
        return wateringService.reboot(pumpId, user);
    }

    @Transactional(readOnly = true)
    public PumpWateringService.PumpStatusResult status(Integer pumpId, AuthenticatedUser user) {
        return wateringService.status(pumpId, user);
    }

    @Transactional(readOnly = true)
    public PumpAck getAck(String correlationId) {
        return wateringService.getAck(correlationId);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByDeviceId(Integer deviceId, DeviceShadowState state) {
        return queryService.listByDevice(deviceId, state);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByPlantId(Integer plantId) {
        PlantEntity plant = plantId != null ? entityManager.find(PlantEntity.class, plantId) : null;
        return queryService.listByPlant(plant);
    }

    public record PumpBindingItem(Integer plantId, Integer rateMlPerHour) {
    }

    public record PumpWateringRequest(Integer durationS, Double waterVolumeL, Double ph, String fertilizersPerLiter) {
    }
}

