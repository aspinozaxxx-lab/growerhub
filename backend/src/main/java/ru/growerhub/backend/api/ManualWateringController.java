package ru.growerhub.backend.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.ManualWateringDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantEntity;
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.device.DeviceService;
import ru.growerhub.backend.mqtt.AckStore;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.mqtt.model.CmdPumpStart;
import ru.growerhub.backend.mqtt.model.CmdPumpStop;
import ru.growerhub.backend.mqtt.model.CmdReboot;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;
import ru.growerhub.backend.mqtt.model.ManualWateringState;
import ru.growerhub.backend.user.UserEntity;

@RestController
@Validated
public class ManualWateringController {
    private final DeviceRepository deviceRepository;
    private final PlantDeviceRepository plantDeviceRepository;
    private final PlantJournalEntryRepository plantJournalEntryRepository;
    private final AckStore ackStore;
    private final DeviceService deviceService;
    private final ObjectProvider<MqttPublisher> publisherProvider;

    public ManualWateringController(
            DeviceRepository deviceRepository,
            PlantDeviceRepository plantDeviceRepository,
            PlantJournalEntryRepository plantJournalEntryRepository,
            AckStore ackStore,
            DeviceService deviceService,
            ObjectProvider<MqttPublisher> publisherProvider
    ) {
        this.deviceRepository = deviceRepository;
        this.plantDeviceRepository = plantDeviceRepository;
        this.plantJournalEntryRepository = plantJournalEntryRepository;
        this.ackStore = ackStore;
        this.deviceService = deviceService;
        this.publisherProvider = publisherProvider;
    }

    @PostMapping("/api/manual-watering/start")
    @Transactional
    public ManualWateringDtos.ManualWateringStartResponse start(
            @Valid @RequestBody ManualWateringDtos.ManualWateringStartRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        DeviceEntity device = requireDeviceAccess(request.deviceId(), user);
        Integer ownerUserId = device.getUser() != null ? device.getUser().getId() : user.getId();
        List<PlantEntity> plants = getLinkedPlants(device, ownerUserId);
        if (plants.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Устройство не привязано ни k odnomu rasteniyu");
        }

        Map<String, Object> view = deviceService.getManualWateringView(request.deviceId());
        if (view != null && "running".equals(asString(view.get("status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "Полив уже выполняется — повторный запуск запрещён.");
        }

        Integer durationS = request.durationS();
        Double calculatedWaterUsed = null;
        if (request.waterVolumeL() != null && device.getWateringSpeedLph() != null && device.getWateringSpeedLph() > 0) {
            double seconds = request.waterVolumeL() / device.getWateringSpeedLph() * 3600.0;
            durationS = Math.max(1, (int) Math.ceil(seconds));
            calculatedWaterUsed = request.waterVolumeL();
        }

        if (durationS == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ukazhite water_volume_l ili duration_s dlya starta poliva");
        }

        double waterVolumeL = resolveWaterVolumeL(device, durationS, calculatedWaterUsed, request);

        String correlationId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        CmdPumpStart cmd = new CmdPumpStart("pump.start", correlationId, startedAt, durationS);
        try {
            getPublisher().publishCmd(request.deviceId(), cmd);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to publish manual watering command");
        }

        ManualWateringState manualState = new ManualWateringState(
                "running",
                durationS,
                startedAt,
                durationS,
                correlationId
        );
        DeviceState state = new DeviceState(manualState, null, null, null, null, null, null, null, null, null);
        deviceService.handleState(request.deviceId(), state, startedAt);

        createWateringJournal(
                device,
                plants,
                user,
                startedAt,
                durationS,
                waterVolumeL,
                request
        );

        return new ManualWateringDtos.ManualWateringStartResponse(correlationId);
    }

    @PostMapping("/api/manual-watering/stop")
    public ManualWateringDtos.ManualWateringStopResponse stop(
            @Valid @RequestBody ManualWateringDtos.ManualWateringStopRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireDeviceAccess(request.deviceId(), user);

        Map<String, Object> view = deviceService.getManualWateringView(request.deviceId());
        if (view == null || !"running".equals(asString(view.get("status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "Полив не выполняется — останавливать нечего.");
        }

        String correlationId = UUID.randomUUID().toString().replace("-", "");
        CmdPumpStop cmd = new CmdPumpStop("pump.stop", correlationId, LocalDateTime.now(ZoneOffset.UTC));
        try {
            getPublisher().publishCmd(request.deviceId(), cmd);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to publish manual watering command");
        }

        return new ManualWateringDtos.ManualWateringStopResponse(correlationId);
    }

    @PostMapping("/api/manual-watering/reboot")
    public ManualWateringDtos.ManualWateringRebootResponse reboot(
            @Valid @RequestBody ManualWateringDtos.ManualWateringRebootRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireDeviceAccess(request.deviceId(), user);

        String correlationId = UUID.randomUUID().toString().replace("-", "");
        long issuedAt = Instant.now().getEpochSecond();
        CmdReboot cmd = new CmdReboot("reboot", correlationId, issuedAt);
        try {
            getPublisher().publishCmd(request.deviceId(), cmd);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to publish manual reboot command");
        }

        return new ManualWateringDtos.ManualWateringRebootResponse(correlationId, "reboot command published");
    }

    @GetMapping("/api/manual-watering/status")
    public ManualWateringDtos.ManualWateringStatusResponse status(
            @RequestParam("device_id") String deviceId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireDeviceAccess(deviceId, user);
        return deviceService.buildManualWateringStatus(deviceId);
    }

    @GetMapping("/api/manual-watering/ack")
    public ManualWateringDtos.ManualWateringAckResponse ack(
            @RequestParam("correlation_id") String correlationId,
            @AuthenticationPrincipal UserEntity user
    ) {
        ManualWateringAck ack = getAckStore().get(correlationId);
        if (ack == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACK ещё не получен или удалён по TTL");
        }
        return new ManualWateringDtos.ManualWateringAckResponse(
                ack.correlationId(),
                ack.result(),
                ack.reason(),
                ack.status()
        );
    }

    @GetMapping("/api/manual-watering/wait-ack")
    public ManualWateringDtos.ManualWateringAckResponse waitAck(
            @RequestParam("correlation_id") String correlationId,
            @RequestParam(value = "timeout_s", defaultValue = "5") @Min(1) @Max(15) Integer timeoutSeconds,
            @AuthenticationPrincipal UserEntity user
    ) {
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (true) {
            ManualWateringAck ack = getAckStore().get(correlationId);
            if (ack != null) {
                return new ManualWateringDtos.ManualWateringAckResponse(
                        ack.correlationId(),
                        ack.result(),
                        ack.reason(),
                        ack.status()
                );
            }
            if (System.nanoTime() >= deadline) {
                throw new ApiException(
                        HttpStatus.REQUEST_TIMEOUT,
                        "ACK не получен в заданное время"
                );
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiException(
                        HttpStatus.REQUEST_TIMEOUT,
                        "ACK не получен в заданное время"
                );
            }
        }
    }

    private DeviceEntity requireDeviceAccess(String deviceId, UserEntity user) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }
        if (!"admin".equals(user.getRole()) && (device.getUser() == null || !device.getUser().getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo ustrojstva");
        }
        return device;
    }

    private List<PlantEntity> getLinkedPlants(DeviceEntity device, Integer ownerUserId) {
        List<PlantDeviceEntity> links = plantDeviceRepository.findAllByDevice_Id(device.getId());
        List<PlantEntity> plants = new ArrayList<>();
        for (PlantDeviceEntity link : links) {
            PlantEntity plant = link.getPlant();
            if (plant == null) {
                continue;
            }
            if (ownerUserId != null) {
                UserEntity owner = plant.getUser();
                if (owner == null || !ownerUserId.equals(owner.getId())) {
                    continue;
                }
            }
            plants.add(plant);
        }
        return plants;
    }

    private double resolveWaterVolumeL(
            DeviceEntity device,
            int durationS,
            Double waterUsed,
            ManualWateringDtos.ManualWateringStartRequest payload
    ) {
        if (waterUsed != null) {
            return waterUsed;
        }
        if (payload.waterVolumeL() != null) {
            return payload.waterVolumeL();
        }
        if (device.getWateringSpeedLph() != null) {
            return device.getWateringSpeedLph() * durationS / 3600.0;
        }
        return 0.0;
    }

    private void createWateringJournal(
            DeviceEntity device,
            List<PlantEntity> plants,
            UserEntity currentUser,
            LocalDateTime startedAt,
            int durationS,
            double waterVolumeL,
            ManualWateringDtos.ManualWateringStartRequest payload
    ) {
        if (plants.isEmpty()) {
            return;
        }
        UserEntity owner = device.getUser() != null ? device.getUser() : currentUser;
        String text = buildWateringText(durationS, waterVolumeL, payload);
        for (PlantEntity plant : plants) {
            PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
            entry.setPlant(plant);
            entry.setUser(owner);
            entry.setType("watering");
            entry.setText(text);
            entry.setEventAt(startedAt);
            PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
            details.setJournalEntry(entry);
            details.setWaterVolumeL(waterVolumeL);
            details.setDurationS(durationS);
            details.setPh(payload.ph());
            details.setFertilizersPerLiter(payload.fertilizersPerLiter());
            entry.setWateringDetails(details);
            plantJournalEntryRepository.save(entry);
        }
    }

    private String buildWateringText(
            int durationS,
            double waterVolumeL,
            ManualWateringDtos.ManualWateringStartRequest payload
    ) {
        List<String> parts = new ArrayList<>();
        parts.add(String.format(java.util.Locale.US, "obem_vody=%.2fl", waterVolumeL));
        parts.add("dlitelnost=" + durationS + "s");
        if (payload.ph() != null) {
            parts.add("ph=" + payload.ph());
        }
        if (payload.fertilizersPerLiter() != null && !payload.fertilizersPerLiter().isEmpty()) {
            parts.add("udobreniya_na_litr=" + payload.fertilizersPerLiter() + " (udobreniya ukazany na litr)");
        }
        return String.join("; ", parts);
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private MqttPublisher getPublisher() {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT publisher unavailable");
        }
        return publisher;
    }

    private AckStore getAckStore() {
        if (ackStore == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Ack store unavailable");
        }
        return ackStore;
    }
}
