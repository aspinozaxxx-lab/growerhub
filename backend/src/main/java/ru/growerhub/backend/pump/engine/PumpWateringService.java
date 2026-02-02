package ru.growerhub.backend.pump.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.pump.PumpWateringSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpRebootResult;
import ru.growerhub.backend.pump.contract.PumpStartResult;
import ru.growerhub.backend.pump.contract.PumpStatusResult;
import ru.growerhub.backend.pump.contract.PumpStopResult;
import ru.growerhub.backend.pump.contract.PumpCommandGateway;
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
    private final DeviceFacade deviceFacade;
    private final PumpCommandGateway commandGateway;
    private final PumpWateringSettings wateringSettings;

    public PumpWateringService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            JournalFacade journalFacade,
            PlantFacade plantFacade,
            @Lazy DeviceFacade deviceFacade,
            PumpCommandGateway commandGateway,
            PumpWateringSettings wateringSettings
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.journalFacade = journalFacade;
        this.plantFacade = plantFacade;
        this.deviceFacade = deviceFacade;
        this.commandGateway = commandGateway;
        this.wateringSettings = wateringSettings;
    }

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

        Double plannedWaterVolumeL = request.waterVolumeL();
        if (plannedWaterVolumeL == null) {
            plannedWaterVolumeL = calculateTotalVolumeFromDuration(bindings, durationS);
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
                correlationId,
                plannedWaterVolumeL,
                request.ph(),
                request.fertilizersPerLiter(),
                null
        );
        deviceFacade.updateManualWateringState(deviceId, manualState, startedAt);

        return new PumpStartResult(correlationId);
    }

    public PumpStopResult stop(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        String deviceId = resolveDeviceId(pump);
        if (deviceId == null) {
            throw new DomainException("not_found", "device not found for pump");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        commandGateway.publishStop(deviceId, correlationId, now);
        finalizeWateringIfNeeded(pump, deviceId, user, true, now);
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        finalizeWateringIfNeeded(pump, deviceId, user, false, now);
        Map<String, Object> view = deviceFacade.getManualWateringView(deviceId);
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
            int rate = binding.getRateMlPerHour() != null
                    ? binding.getRateMlPerHour()
                    : wateringSettings.getDefaultRateMlPerHour();
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
            int rate = binding.getRateMlPerHour() != null
                    ? binding.getRateMlPerHour()
                    : wateringSettings.getDefaultRateMlPerHour();
            double volumeL = rate / 1000.0 * durationS / 3600.0;
            targets.add(new JournalFacade.WateringTarget(plantId, durationS, volumeL));
        }
        return targets;
    }

    private Double calculateTotalVolumeFromDuration(List<PumpPlantBindingEntity> bindings, int durationS) {
        if (bindings == null || bindings.isEmpty() || durationS <= 0) {
            return null;
        }
        double totalVolumeL = 0.0;
        for (PumpPlantBindingEntity binding : bindings) {
            int rate = binding.getRateMlPerHour() != null
                    ? binding.getRateMlPerHour()
                    : wateringSettings.getDefaultRateMlPerHour();
            totalVolumeL += rate / 1000.0 * durationS / 3600.0;
        }
        return totalVolumeL > 0.0 ? totalVolumeL : null;
    }

    private int calculateActualDurationSeconds(LocalDateTime startedAt, LocalDateTime now, Integer plannedDurationS) {
        if (startedAt == null || now == null) {
            return 0;
        }
        long elapsed = Duration.between(startedAt, now).getSeconds();
        if (elapsed < 0) {
            elapsed = 0;
        }
        if (plannedDurationS != null) {
            elapsed = Math.min(elapsed, plannedDurationS);
        }
        if (elapsed > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) elapsed;
    }

    private boolean shouldFinalizeWatering(DeviceShadowState.ManualWateringState manual, LocalDateTime now) {
        if (manual == null) {
            return false;
        }
        String status = manual.status();
        if (status != null && !"running".equalsIgnoreCase(status)) {
            return true;
        }
        Integer remainingS = manual.remainingS();
        if (remainingS != null && remainingS <= 0) {
            return true;
        }
        Integer plannedDurationS = manual.durationS();
        if (plannedDurationS != null && manual.startedAt() != null) {
            long elapsed = Duration.between(manual.startedAt(), now).getSeconds();
            return elapsed >= plannedDurationS;
        }
        return false;
    }

    private List<JournalFacade.WateringTarget> remapTargetsWithDuration(
            List<JournalFacade.WateringTarget> targets,
            Integer plannedDurationS,
            int actualDurationS,
            double plannedVolumeScale
    ) {
        List<JournalFacade.WateringTarget> mapped = new ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return mapped;
        }
        boolean hasPlannedDuration = plannedDurationS != null && plannedDurationS > 0;
        for (JournalFacade.WateringTarget target : targets) {
            if (target == null || target.plantId() == null) {
                continue;
            }
            double volumeL = target.waterVolumeL() * plannedVolumeScale;
            if (hasPlannedDuration) {
                volumeL = calculateActualVolumeL(volumeL, plannedDurationS, actualDurationS);
            } else {
                volumeL = roundVolumeL(volumeL);
            }
            mapped.add(new JournalFacade.WateringTarget(
                    target.plantId(),
                    actualDurationS,
                    volumeL
            ));
        }
        return mapped;
    }

    private void finalizeWateringIfNeeded(
            PumpEntity pump,
            String deviceId,
            AuthenticatedUser user,
            boolean forceFinish,
            LocalDateTime now
    ) {
        DeviceShadowState shadow = deviceFacade.getShadowState(deviceId);
        DeviceShadowState.ManualWateringState manual = shadow != null ? shadow.manualWatering() : null;
        if (manual == null) {
            return;
        }
        String correlationId = manual.correlationId();
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        if (correlationId.equals(manual.journalWrittenForCorrelationId())) {
            return;
        }
        if (!forceFinish && !shouldFinalizeWatering(manual, now)) {
            return;
        }
        LocalDateTime startedAt = manual.startedAt();
        if (startedAt == null) {
            return;
        }
        Integer plannedDurationS = manual.durationS();
        int actualDurationS = calculateActualDurationSeconds(startedAt, now, plannedDurationS);
        int volumeDurationS = plannedDurationS != null ? plannedDurationS : actualDurationS;
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPump_Id(pump.getId());
        List<JournalFacade.WateringTarget> plannedTargets = buildTargets(bindings, volumeDurationS);
        double plannedVolumeScale = calculatePlannedVolumeScale(plannedTargets, manual.waterVolumeL());
        List<JournalFacade.WateringTarget> actualTargets = remapTargetsWithDuration(
                plannedTargets,
                plannedDurationS,
                actualDurationS,
                plannedVolumeScale
        );
        journalFacade.createWateringEntries(
                actualTargets,
                user,
                startedAt,
                manual.ph(),
                manual.fertilizersPerLiter()
        );
        for (JournalFacade.WateringTarget target : actualTargets) {
            plantFacade.recordWateringEvent(target.plantId(), target.waterVolumeL(), startedAt);
        }
        DeviceShadowState.ManualWateringState completed = new DeviceShadowState.ManualWateringState(
                "completed",
                volumeDurationS,
                startedAt,
                0,
                correlationId,
                manual.waterVolumeL(),
                manual.ph(),
                manual.fertilizersPerLiter(),
                correlationId
        );
        deviceFacade.updateManualWateringState(deviceId, completed, now);
    }

    private double calculateActualVolumeL(double plannedVolumeL, int plannedDurationS, int actualDurationS) {
        if (plannedDurationS <= 0) {
            return roundVolumeL(plannedVolumeL);
        }
        int clampedDurationS = Math.max(0, Math.min(actualDurationS, plannedDurationS));
        double volumeL = plannedVolumeL * (double) clampedDurationS / (double) plannedDurationS;
        double clampedVolume = Math.max(0.0, Math.min(volumeL, plannedVolumeL));
        return roundVolumeL(clampedVolume);
    }

    private double calculatePlannedVolumeScale(
            List<JournalFacade.WateringTarget> plannedTargets,
            Double plannedTotalVolumeL
    ) {
        if (plannedTotalVolumeL == null || plannedTargets == null || plannedTargets.isEmpty()) {
            return 1.0;
        }
        double sum = 0.0;
        for (JournalFacade.WateringTarget target : plannedTargets) {
            if (target == null) {
                continue;
            }
            sum += target.waterVolumeL();
        }
        if (sum <= 0.0) {
            return 1.0;
        }
        return plannedTotalVolumeL / sum;
    }

    private double roundVolumeL(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
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

    private String resolveDeviceId(PumpEntity pump) {
        DeviceSummary summary = resolveDeviceSummary(pump);
        return summary != null ? summary.deviceId() : null;
    }

    private DeviceSummary resolveDeviceSummary(PumpEntity pump) {
        if (pump == null || pump.getDeviceId() == null) {
            return null;
        }
        return deviceFacade.getDeviceSummary(pump.getDeviceId());
    }
}





