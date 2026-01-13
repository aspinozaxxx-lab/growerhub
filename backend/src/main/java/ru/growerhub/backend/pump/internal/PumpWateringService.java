package ru.growerhub.backend.pump.internal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.pump.PumpAck;
import ru.growerhub.backend.pump.PumpCommandGateway;
import ru.growerhub.backend.pump.PumpEntity;
import ru.growerhub.backend.pump.PumpPlantBindingEntity;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.facade.PlantFacade;

@Service
public class PumpWateringService {
    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final JournalFacade journalFacade;
    private final PlantFacade plantFacade;
    private final DeviceFacade deviceFacade;
    private final PumpCommandGateway commandGateway;

    public PumpWateringService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            JournalFacade journalFacade,
            PlantFacade plantFacade,
            DeviceFacade deviceFacade,
            PumpCommandGateway commandGateway
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.journalFacade = journalFacade;
        this.plantFacade = plantFacade;
        this.deviceFacade = deviceFacade;
        this.commandGateway = commandGateway;
    }

    @Transactional
    public PumpStartResult start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPump_Id(pumpId);
        if (bindings.isEmpty()) {
            throw new DomainException("bad_request", "nasos ne privyazan ni k odnomu rasteniyu");
        }

        Integer durationS = request.durationS();
        if (durationS == null && request.waterVolumeL() != null) {
            durationS = calculateDurationFromVolume(bindings, request.waterVolumeL());
        }
        if (durationS == null || durationS <= 0) {
            throw new DomainException("bad_request", "ukazhite water_volume_l ili duration_s dlya starta poliva");
        }

        String correlationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        commandGateway.publishStart(pump.getDevice().getDeviceId(), correlationId, startedAt, durationS);

        DeviceShadowState.ManualWateringState manualState = new DeviceShadowState.ManualWateringState(
                "running",
                durationS,
                startedAt,
                durationS,
                correlationId
        );
        deviceFacade.updateManualWateringState(pump.getDevice().getDeviceId(), manualState, startedAt);

        List<JournalFacade.WateringTarget> targets = buildTargets(bindings, durationS);
        journalFacade.createWateringEntries(targets, user, startedAt, request.ph(), request.fertilizersPerLiter());
        for (JournalFacade.WateringTarget target : targets) {
            plantFacade.recordWateringEvent(target.plantId(), target.waterVolumeL(), startedAt);
        }

        return new PumpStartResult(correlationId);
    }

    public PumpStopResult stop(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        commandGateway.publishStop(pump.getDevice().getDeviceId(), correlationId, LocalDateTime.now(ZoneOffset.UTC));
        return new PumpStopResult(correlationId);
    }

    public PumpRebootResult reboot(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        long issuedAt = Instant.now().getEpochSecond();
        commandGateway.publishReboot(pump.getDevice().getDeviceId(), correlationId, issuedAt);
        return new PumpRebootResult(correlationId, "reboot command published");
    }

    public PumpStatusResult status(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        Map<String, Object> view = deviceFacade.getManualWateringView(pump.getDevice().getDeviceId());
        return new PumpStatusResult(view);
    }

    public PumpAck getAck(String correlationId) {
        return commandGateway.getAck(correlationId);
    }

    private int calculateDurationFromVolume(List<PumpPlantBindingEntity> bindings, double volumeL) {
        if (bindings.isEmpty()) {
            return 0;
        }
        double sumRate = 0.0;
        for (PumpPlantBindingEntity binding : bindings) {
            int rate = binding.getRateMlPerHour() != null ? binding.getRateMlPerHour() : 2000;
            sumRate += rate;
        }
        double avgRateMlPerHour = sumRate / bindings.size();
        if (avgRateMlPerHour <= 0) {
            return 0;
        }
        double avgRateLph = avgRateMlPerHour / 1000.0;
        return (int) Math.max(1, Math.ceil(volumeL / avgRateLph * 3600.0));
    }

    private List<JournalFacade.WateringTarget> buildTargets(List<PumpPlantBindingEntity> bindings, int durationS) {
        List<JournalFacade.WateringTarget> targets = new ArrayList<>();
        for (PumpPlantBindingEntity binding : bindings) {
            PlantEntity plant = binding.getPlant();
            if (plant == null) {
                continue;
            }
            int rate = binding.getRateMlPerHour() != null ? binding.getRateMlPerHour() : 2000;
            double volumeL = rate / 1000.0 * durationS / 3600.0;
            targets.add(new JournalFacade.WateringTarget(plant.getId(), durationS, volumeL));
        }
        return targets;
    }

    private PumpEntity requirePumpAccess(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new DomainException("not_found", "nasos ne naiden");
        }
        if (user == null) {
            throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
        }
        if (!user.isAdmin()) {
            if (pump.getDevice() == null || pump.getDevice().getUser() == null) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
            }
            if (!pump.getDevice().getUser().getId().equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
            }
        }
        return pump;
    }

    public record PumpWateringRequest(Integer durationS, Double waterVolumeL, Double ph, String fertilizersPerLiter) {
    }

    public record PumpStartResult(String correlationId) {
    }

    public record PumpStopResult(String correlationId) {
    }

    public record PumpRebootResult(String correlationId, String message) {
    }

    public record PumpStatusResult(Map<String, Object> view) {
    }
}


