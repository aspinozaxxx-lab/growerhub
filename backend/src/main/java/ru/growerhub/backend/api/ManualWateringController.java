package ru.growerhub.backend.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantEntity;
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.mqtt.AckStore;
import ru.growerhub.backend.mqtt.DeviceShadowStore;
import ru.growerhub.backend.mqtt.ManualWateringSettings;
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
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final PlantDeviceRepository plantDeviceRepository;
    private final PlantJournalEntryRepository plantJournalEntryRepository;
    private final AckStore ackStore;
    private final DeviceShadowStore shadowStore;
    private final ManualWateringSettings settings;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MqttPublisher> publisherProvider;

    public ManualWateringController(
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            PlantDeviceRepository plantDeviceRepository,
            PlantJournalEntryRepository plantJournalEntryRepository,
            AckStore ackStore,
            DeviceShadowStore shadowStore,
            ManualWateringSettings settings,
            ObjectMapper objectMapper,
            ObjectProvider<MqttPublisher> publisherProvider
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.plantDeviceRepository = plantDeviceRepository;
        this.plantJournalEntryRepository = plantJournalEntryRepository;
        this.ackStore = ackStore;
        this.shadowStore = shadowStore;
        this.settings = settings;
        this.objectMapper = objectMapper;
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

        DeviceShadowStore store = getShadowStore();
        Map<String, Object> view = store.getManualWateringView(request.deviceId());
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
        DeviceState state = new DeviceState(manualState, null, null, null, null);
        store.updateFromState(request.deviceId(), state);
        upsertDeviceState(request.deviceId(), state, startedAt);

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

        DeviceShadowStore store = getShadowStore();
        Map<String, Object> view = store.getManualWateringView(request.deviceId());
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
        int threshold = resolveThreshold();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Map<String, Object> view = getShadowStore().getManualWateringView(deviceId);
        if (view == null) {
            DeviceStateLastEntity storedState = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
            if (storedState != null) {
                Map<String, Object> statePayload = parseStateJson(storedState.getStateJson());
                Map<String, Object> manual = extractManual(statePayload);
                String statusValue = manual != null && manual.get("status") != null
                        ? manual.get("status").toString()
                        : "idle";
                Integer durationS = asInteger(manual != null ? manual.get("duration_s") : null);
                String startedAt = asString(manual != null ? manual.get("started_at") : null);
                String correlationId = asString(manual != null ? manual.get("correlation_id") : null);
                Integer remainingS = asInteger(manual != null ? manual.get("remaining_s") : null);
                LocalDateTime updatedAt = storedState.getUpdatedAt();
                String lastSeenIso = formatIsoUtc(updatedAt);
                boolean isOnline = updatedAt != null && Duration.between(updatedAt, now).getSeconds() <= threshold;
                String offlineReason = isOnline ? null : "device_offline";
                if (!isOnline && offlineReason == null) {
                    offlineReason = "device_offline";
                }
                return new ManualWateringDtos.ManualWateringStatusResponse(
                        statusValue,
                        durationS,
                        durationS,
                        startedAt,
                        startedAt,
                        remainingS,
                        correlationId,
                        lastSeenIso,
                        lastSeenIso,
                        isOnline,
                        offlineReason,
                        "db_state"
                );
            }

            DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device == null) {
                return new ManualWateringDtos.ManualWateringStatusResponse(
                        "idle",
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "no_state_yet",
                        "fallback"
                );
            }

            LocalDateTime lastSeen = device.getLastSeen();
            String lastSeenIso = formatIsoUtc(lastSeen);
            boolean isOnline = lastSeen != null && Duration.between(lastSeen, now).getSeconds() <= threshold;
            String offlineReason = isOnline ? null : "device_offline";
            return new ManualWateringDtos.ManualWateringStatusResponse(
                    "idle",
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    lastSeenIso,
                    lastSeenIso,
                    isOnline,
                    offlineReason,
                    "db_fallback"
            );
        }

        Map<String, Object> enriched = new HashMap<>(view);
        boolean hasLastSeen = enriched.containsKey("last_seen_at") && enriched.get("last_seen_at") != null;
        boolean hasIsOnline = enriched.containsKey("is_online") && enriched.get("is_online") != null;
        if (!hasLastSeen || !hasIsOnline) {
            DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device != null) {
                LocalDateTime lastSeen = device.getLastSeen();
                if (!hasLastSeen) {
                    String lastSeenIso = formatIsoUtc(lastSeen);
                    enriched.put("last_seen_at", lastSeenIso);
                    if (!enriched.containsKey("updated_at") || enriched.get("updated_at") == null) {
                        enriched.put("updated_at", lastSeenIso);
                    }
                }
                if (!hasIsOnline) {
                    boolean isOnline = lastSeen != null && Duration.between(lastSeen, now).getSeconds() <= threshold;
                    enriched.put("is_online", isOnline);
                }
                if (!enriched.containsKey("updated_at")) {
                    enriched.put("updated_at", enriched.get("last_seen_at"));
                }
            } else {
                if (!hasLastSeen) {
                    enriched.put("last_seen_at", null);
                }
                if (!hasIsOnline) {
                    enriched.put("is_online", false);
                }
                enriched.putIfAbsent("updated_at", enriched.get("last_seen_at"));
            }
        }

        Boolean isOnline = asBoolean(enriched.get("is_online"));
        enriched.putIfAbsent("updated_at", null);
        enriched.putIfAbsent("last_seen_at", null);
        enriched.putIfAbsent("start_time", enriched.get("started_at"));
        enriched.putIfAbsent("duration", enriched.get("duration_s"));
        enriched.put("offline_reason", Boolean.TRUE.equals(isOnline) ? null : "device_offline");

        return new ManualWateringDtos.ManualWateringStatusResponse(
                asString(enriched.get("status")),
                asInteger(enriched.get("duration_s")),
                asInteger(enriched.get("duration")),
                asString(enriched.get("started_at")),
                asString(enriched.get("start_time")),
                asInteger(enriched.get("remaining_s")),
                asString(enriched.get("correlation_id")),
                asString(enriched.get("updated_at")),
                asString(enriched.get("last_seen_at")),
                isOnline,
                asString(enriched.get("offline_reason")),
                asString(enriched.get("source"))
        );
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

    private void upsertDeviceState(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        DeviceStateLastEntity record = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        if (record == null) {
            record = DeviceStateLastEntity.create();
            record.setDeviceId(deviceId);
        }
        record.setStateJson(payload);
        record.setUpdatedAt(updatedAt);
        deviceStateLastRepository.save(record);
    }

    private Map<String, Object> parseStateJson(String stateJson) {
        if (stateJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(stateJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> extractManual(Map<String, Object> statePayload) {
        if (statePayload == null) {
            return null;
        }
        Object manual = statePayload.get("manual_watering");
        if (manual instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return null;
    }

    private String formatIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).withNano(0).format(ISO_UTC);
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private int resolveThreshold() {
        int threshold = settings.getDeviceOnlineThresholdSeconds();
        return threshold > 0 ? threshold : 180;
    }

    private MqttPublisher getPublisher() {
        MqttPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MQTT publisher unavailable");
        }
        return publisher;
    }

    private DeviceShadowStore getShadowStore() {
        if (shadowStore == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Device shadow store unavailable");
        }
        return shadowStore;
    }

    private AckStore getAckStore() {
        if (ackStore == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Ack store unavailable");
        }
        return ackStore;
    }
}
