package ru.growerhub.backend.pump.engine;

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
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.pump.PumpAck;
import ru.growerhub.backend.pump.PumpCommandGateway;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.plant.PlantFacade;

@Service
public class PumpWateringService {
    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final JournalFacade journalFacade;
    private final PlantFacade plantFacade;
    private final DeviceAccessService deviceAccessService;
    private final PumpCommandGateway commandGateway;

    public PumpWateringService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            JournalFacade journalFacade,
            PlantFacade plantFacade,
            DeviceAccessService deviceAccessService,
            PumpCommandGateway commandGateway
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.journalFacade = journalFacade;
        this.plantFacade = plantFacade;
        this.deviceAccessService = deviceAccessService;
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
        String deviceId = resolveDeviceId(pump);
        if (deviceId == null) {
            throw new DomainException("not_found", "device not found for pump");
        }
        commandGateway.publishStart(deviceId, correlationId, startedAt, durationS);

        DeviceShadowState.ManualWateringState manualState = new DeviceShadowState.ManualWateringState(
                "running",
                durationS,
                startedAt,
                durationS,
                correlationId
        );
        deviceAccessService.updateManualWateringState(deviceId, manualState, startedAt);

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
        String deviceId = resolveDeviceId(pump);
        if (deviceId == null) {
            throw new DomainException("not_found", "device not found for pump");
        }
        commandGateway.publishStop(deviceId, correlationId, LocalDateTime.now(ZoneOffset.UTC));
        return new PumpStopResult(correlationId);
    }

    public PumpRebootResult reboot(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String deviceId = resolveDeviceId(pump);
        if (deviceId == null) {
            throw new DomainException("not_found", "device not found for pump");
        }
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        long issuedAt = Instant.now().getEpochSecond();
        commandGateway.publishReboot(deviceId, correlationId, issuedAt);
        return new PumpRebootResult(correlationId, "reboot command published");
    }

    public PumpStatusResult status(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String deviceId = resolveDeviceId(pump);
        if (deviceId == null) {
            throw new DomainException("not_found", "device not found for pump");
        }
        Map<String, Object> view = deviceAccessService.getManualWateringView(deviceId);
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
            Integer plantId = binding.getPlantId();
            if (plantId == null) {
                continue;
            }
            int rate = binding.getRateMlPerHour() != null ? binding.getRateMlPerHour() : 2000;
            double volumeL = rate / 1000.0 * durationS / 3600.0;
            targets.add(new JournalFacade.WateringTarget(plantId, durationS, volumeL));
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
            DeviceSummary summary = resolveDeviceSummary(pump);
            Integer ownerId = summary != null ? summary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
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

    private String resolveDeviceId(PumpEntity pump) {
        DeviceSummary summary = resolveDeviceSummary(pump);
        return summary != null ? summary.deviceId() : null;
    }

    private DeviceSummary resolveDeviceSummary(PumpEntity pump) {
        if (pump == null || pump.getDeviceId() == null) {
            return null;
        }
        return deviceAccessService.getDeviceSummary(pump.getDeviceId());
    }
}




