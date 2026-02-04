package ru.growerhub.backend.pump;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpRebootResult;
import ru.growerhub.backend.pump.contract.PumpStartResult;
import ru.growerhub.backend.pump.contract.PumpStatusResult;
import ru.growerhub.backend.pump.contract.PumpStopResult;
import ru.growerhub.backend.pump.engine.PumpBindingService;
import ru.growerhub.backend.pump.engine.PumpQueryService;
import ru.growerhub.backend.pump.engine.PumpService;
import ru.growerhub.backend.pump.engine.PumpWateringService;
import ru.growerhub.backend.pump.contract.PumpView;

@Service
public class PumpFacade {
    private final PumpBindingService bindingService;
    private final PumpWateringService wateringService;
    private final PumpQueryService queryService;
    private final PumpService pumpService;

    public PumpFacade(
            PumpBindingService bindingService,
            PumpWateringService wateringService,
            PumpQueryService queryService,
            PumpService pumpService
    ) {
        this.bindingService = bindingService;
        this.wateringService = wateringService;
        this.queryService = queryService;
        this.pumpService = pumpService;
    }

    @Transactional
    public void updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user) {
        List<PumpBindingService.PumpBindingItem> mapped = items != null
                ? items.stream()
                .map(item -> new PumpBindingService.PumpBindingItem(
                        item.plantId(),
                        item.rateMlPerHour()
                ))
                .toList()
                : null;
        bindingService.updateBindings(pumpId, mapped, user);
    }

    @Transactional
    public PumpStartResult start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user) {
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
    public PumpStopResult stop(Integer pumpId, AuthenticatedUser user) {
        return wateringService.stop(pumpId, user);
    }

    @Transactional
    public PumpRebootResult reboot(Integer pumpId, AuthenticatedUser user) {
        return wateringService.reboot(pumpId, user);
    }

    @Transactional
    public PumpStatusResult status(Integer pumpId, AuthenticatedUser user) {
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
        return queryService.listByPlantId(plantId);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByPlantIdLight(Integer plantId) {
        return queryService.listByPlantIdLight(plantId);
    }

    @Transactional
    public void ensureDefaultPump(Integer deviceId) {
        pumpService.ensureDefaultPump(deviceId);
    }

    @Transactional
    public void deleteByDeviceId(Integer deviceId) {
        pumpService.deleteAllByDeviceId(deviceId);
    }

    public record PumpBindingItem(Integer plantId, Integer rateMlPerHour) {
    }

    public record PumpWateringRequest(Integer durationS, Double waterVolumeL, Double ph, String fertilizersPerLiter) {
    }
}


