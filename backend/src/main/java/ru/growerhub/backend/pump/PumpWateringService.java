package ru.growerhub.backend.pump;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.device.DeviceShadowStore;
import ru.growerhub.backend.journal.JournalService;
import ru.growerhub.backend.mqtt.AckStore;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.mqtt.model.ManualWateringState;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantHistoryService;
import ru.growerhub.backend.user.UserEntity;

@Service
public class PumpWateringService {
    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final JournalService journalService;
    private final PlantHistoryService plantHistoryService;
    private final DeviceShadowStore shadowStore;
    private final ObjectProvider<MqttPublisher> publisherProvider;
    private final AckStore ackStore;

    public PumpWateringService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            JournalService journalService,
            PlantHistoryService plantHistoryService,
            DeviceShadowStore shadowStore,
            ObjectProvider<MqttPublisher> publisherProvider,
            AckStore ackStore
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.journalService = journalService;
        this.plantHistoryService = plantHistoryService;
        this.shadowStore = shadowStore;
        this.publisherProvider = publisherProvider;
        this.ackStore = ackStore;
    }

    @Transactional
    public PumpStartResult start(Integer pumpId, PumpWateringRequest request, UserEntity user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPump_Id(pumpId);
        if (bindings.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "nasos ne privyazan ni k odnomu rasteniyu");
        }

        Integer durationS = request.durationS();
        if (durationS == null && request.waterVolumeL() != null) {
            durationS = calculateDurationFromVolume(bindings, request.waterVolumeL());
        }
        if (durationS == null || durationS <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ukazhite water_volume_l ili duration_s dlya starta poliva");
        }

        String correlationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        CmdPumpStart cmd = new CmdPumpStart("pump.start", correlationId, startedAt, durationS);
        publishCommand(pump.getDevice().getDeviceId(), cmd);

        ManualWateringState manualState = new ManualWateringState(
                "running",
                durationS,
                startedAt,
                durationS,
                correlationId
        );
        DeviceState state = new DeviceState(manualState, null, null, null, null, null, null, null, null, null);
        shadowStore.updateFromStateAndPersist(pump.getDevice().getDeviceId(), state, startedAt);

        List<JournalService.WateringTarget> targets = buildTargets(bindings, durationS);
        journalService.createWateringEntries(targets, user, startedAt, request.ph(), request.fertilizersPerLiter());
        for (JournalService.WateringTarget target : targets) {
            plantHistoryService.recordWateringEvent(target.plant(), target.waterVolumeL(), startedAt);
        }

        return new PumpStartResult(correlationId);
    }

    public PumpStopResult stop(Integer pumpId, UserEntity user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        CmdPumpStop cmd = new CmdPumpStop("pump.stop", correlationId, LocalDateTime.now(ZoneOffset.UTC));
        publishCommand(pump.getDevice().getDeviceId(), cmd);
        return new PumpStopResult(correlationId);
    }

    public PumpRebootResult reboot(Integer pumpId, UserEntity user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        String correlationId = UUID.randomUUID().toString().replace("-", "");
        long issuedAt = Instant.now().getEpochSecond();
        CmdReboot cmd = new CmdReboot("reboot", correlationId, issuedAt);
        publishCommand(pump.getDevice().getDeviceId(), cmd);
        return new PumpRebootResult(correlationId, "reboot command published");
    }

    public PumpStatusResult status(Integer pumpId, UserEntity user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        Map<String, Object> view = shadowStore.getManualWateringView(pump.getDevice().getDeviceId());
        return new PumpStatusResult(view);
    }

    public ManualWateringAck getAck(String correlationId) {
        return ackStore.get(correlationId);
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

    private List<JournalService.WateringTarget> buildTargets(List<PumpPlantBindingEntity> bindings, int durationS) {
        List<JournalService.WateringTarget> targets = new ArrayList<>();
        for (PumpPlantBindingEntity binding : bindings) {
            PlantEntity plant = binding.getPlant();
            if (plant == null) {
                continue;
            }
            int rate = binding.getRateMlPerHour() != null ? binding.getRateMlPerHour() : 2000;
            double volumeL = rate / 1000.0 * durationS / 3600.0;
            targets.add(new JournalService.WateringTarget(plant, durationS, volumeL));
        }
        return targets;
    }

    private PumpEntity requirePumpAccess(Integer pumpId, UserEntity user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "nasos ne naiden");
        }
        if (!"admin".equals(user.getRole())) {
            if (pump.getDevice() == null || pump.getDevice().getUser() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo nasosa");
            }
            if (!pump.getDevice().getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo nasosa");
            }
        }
        return pump;
    }

    private void publishCommand(String deviceId, Object cmd) {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT publisher unavailable");
        }
        try {
            publisher.publishCmd(deviceId, cmd);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to publish manual watering command");
        }
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
