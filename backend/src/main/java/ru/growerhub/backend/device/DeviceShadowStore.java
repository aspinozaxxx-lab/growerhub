package ru.growerhub.backend.device;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.mqtt.DeviceSettings;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringState;

@Component
public class DeviceShadowStore {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Map<String, ShadowEntry> storage = new ConcurrentHashMap<>();
    private final DeviceSettings settings;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final PlantDeviceRepository plantDeviceRepository;

    public DeviceShadowStore(
            DeviceSettings settings,
            ObjectMapper objectMapper,
            Clock clock,
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            PlantDeviceRepository plantDeviceRepository
    ) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.plantDeviceRepository = plantDeviceRepository;
    }

    // Translitem: obnovlyaem shadow iz poluchennogo state (v RAM).
    public void updateFromState(String deviceId, DeviceState state) {
        updateFromState(deviceId, state, LocalDateTime.now(clock));
    }

    // Translitem: obnovlyaem shadow s yavnym updatedAt (odna tochka dlya MQTT/HTTP).
    public void updateFromState(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        ShadowEntry existing = storage.get(deviceId);
        DeviceMeta meta = existing != null ? existing.meta() : null;
        if (meta == null) {
            DeviceSnapshot loaded = loadFromDb(deviceId);
            if (loaded != null) {
                meta = loaded.meta();
            }
        }
        storage.put(deviceId, new ShadowEntry(state, updatedAt, meta));
    }

    // Translitem: bystryi dostup k state iz pamyati bez meta.
    public DeviceState getLastState(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        return entry != null ? entry.state() : null;
    }

    // Translitem: snapshot bez cold start (ispolzuetsya tol'ko esli meta uzhe est').
    public DeviceSnapshot getSnapshot(String deviceId, Integer userId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry == null || entry.meta() == null) {
            return null;
        }
        DeviceMeta meta = entry.meta();
        if (userId != null && (meta.userId() == null || !meta.userId().equals(userId))) {
            return null;
        }
        LocalDateTime updatedAt = entry.updatedAt();
        return new DeviceSnapshot(meta, entry.state(), updatedAt, isOnline(updatedAt), SnapshotSource.MEMORY);
    }

    // Translitem: snapshot s cold start iz DB, esli v pamyati pustо.
    public DeviceSnapshot getSnapshotOrLoad(String deviceId, Integer userId) {
        DeviceSnapshot snapshot = getSnapshot(deviceId, userId);
        if (snapshot != null) {
            return snapshot;
        }
        DeviceSnapshot loaded = loadFromDb(deviceId);
        if (loaded == null || loaded.meta() == null) {
            return null;
        }
        if (userId != null && (loaded.meta().userId() == null || !loaded.meta().userId().equals(userId))) {
            return null;
        }
        ShadowEntry existing = storage.get(deviceId);
        ShadowEntry merged = mergeEntry(existing, loaded);
        storage.put(deviceId, merged);
        return new DeviceSnapshot(merged.meta(), merged.state(), merged.updatedAt(), isOnline(merged.updatedAt()), loaded.source());
    }

    // Translitem: cold start iz DB (device_state_last + meta + plant links).
    public DeviceSnapshot loadFromDb(String deviceId) {
        if (deviceId == null || deviceRepository == null) {
            return null;
        }
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return null;
        }
        DeviceStateLastEntity stateRecord = deviceStateLastRepository != null
                ? deviceStateLastRepository.findByDeviceId(deviceId).orElse(null)
                : null;
        DeviceState state = null;
        LocalDateTime updatedAt = null;
        SnapshotSource source = SnapshotSource.DB_FALLBACK;
        if (stateRecord != null) {
            state = parseStateJson(stateRecord.getStateJson());
            updatedAt = stateRecord.getUpdatedAt();
            source = SnapshotSource.DB_STATE;
        }
        if (updatedAt == null) {
            updatedAt = device.getLastSeen();
        }
        DeviceMeta meta = buildMeta(device);
        return new DeviceSnapshot(meta, state, updatedAt, isOnline(updatedAt), source);
    }

    public void clear() {
        storage.clear();
    }

    // Translitem: debug-damp s payload (state + meta).
    public Map<String, Object> debugDump(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("state", entry.state() != null
                ? objectMapper.convertValue(entry.state(), new TypeReference<Map<String, Object>>() {
                })
                : null);
        payload.put("meta", entry.meta());
        payload.put("updated_at", formatIsoUtc(entry.updatedAt()));
        return payload;
    }

    // Translitem: sostoyanie poliva dlya UI (manual_watering).
    public Map<String, Object> getManualWateringView(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry == null || entry.state() == null) {
            return null;
        }
        return buildManualWateringView(entry.state(), entry.updatedAt(), "calculated");
    }

    // Translitem: obshchij stroitel' view dlya manual_watering.
    public Map<String, Object> buildManualWateringView(DeviceState state, LocalDateTime observedAt, String source) {
        if (state == null) {
            return null;
        }
        ManualWateringState manual = state.manualWatering();
        String status = manual != null ? manual.status() : null;
        Integer durationS = manual != null ? manual.durationS() : null;
        LocalDateTime startedAt = manual != null ? manual.startedAt() : null;
        String correlationId = manual != null ? manual.correlationId() : null;

        Integer remainingS = null;
        if ("running".equals(status) && durationS != null && startedAt != null) {
            LocalDateTime now = LocalDateTime.now(clock);
            long elapsed = Duration.between(startedAt, now).getSeconds();
            remainingS = (int) Math.max(0, durationS - elapsed);
        }

        boolean isOnline = isOnline(observedAt);
        String lastSeenIso = formatIsoUtc(observedAt);
        String startedIso = startedAt != null ? formatIsoUtc(startedAt) : null;

        Map<String, Object> view = new HashMap<>();
        view.put("status", status);
        view.put("duration_s", durationS);
        view.put("duration", durationS);
        view.put("started_at", startedIso);
        view.put("start_time", startedIso);
        view.put("remaining_s", remainingS);
        view.put("correlation_id", correlationId);
        view.put("updated_at", lastSeenIso);
        view.put("last_seen_at", lastSeenIso);
        view.put("is_online", isOnline);
        view.put("source", source);
        return view;
    }

    private String formatIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).withNano(0).format(ISO_UTC);
    }

    // Translitem: meta dlya response bez sensor state.
    public record DeviceMeta(
            Integer id,
            String deviceId,
            String name,
            Integer userId,
            List<Integer> plantIds,
            Double targetMoisture,
            Integer wateringDuration,
            Integer wateringTimeout,
            Double wateringSpeedLph,
            Integer lightOnHour,
            Integer lightOffHour,
            Integer lightDuration,
            String currentVersion,
            Boolean updateAvailable,
            LocalDateTime lastWatering
    ) {
    }

    // Translitem: snapshot dlya chteniya s fronta (meta + last state).
    public record DeviceSnapshot(
            DeviceMeta meta,
            DeviceState state,
            LocalDateTime updatedAt,
            boolean isOnline,
            SnapshotSource source
    ) {
    }

    // Translitem: istochnik snapshot dlya cold start.
    public enum SnapshotSource {
        MEMORY,
        DB_STATE,
        DB_FALLBACK
    }

    private ShadowEntry mergeEntry(ShadowEntry existing, DeviceSnapshot loaded) {
        DeviceState state = existing != null && existing.state() != null ? existing.state() : loaded.state();
        LocalDateTime updatedAt = existing != null && existing.updatedAt() != null ? existing.updatedAt() : loaded.updatedAt();
        DeviceMeta meta = existing != null && existing.meta() != null ? existing.meta() : loaded.meta();
        return new ShadowEntry(state, updatedAt, meta);
    }

    private DeviceMeta buildMeta(DeviceEntity device) {
        Integer userId = device.getUser() != null ? device.getUser().getId() : null;
        List<Integer> plantIds = new ArrayList<>();
        if (plantDeviceRepository != null) {
            List<PlantDeviceEntity> links = plantDeviceRepository.findAllByDevice_Id(device.getId());
            for (PlantDeviceEntity link : links) {
                if (link.getPlant() != null) {
                    plantIds.add(link.getPlant().getId());
                }
            }
        }
        return new DeviceMeta(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                userId,
                plantIds,
                device.getTargetMoisture(),
                device.getWateringDuration(),
                device.getWateringTimeout(),
                device.getWateringSpeedLph(),
                device.getLightOnHour(),
                device.getLightOffHour(),
                device.getLightDuration(),
                device.getCurrentVersion(),
                device.getUpdateAvailable(),
                device.getLastWatering()
        );
    }

    private boolean isOnline(LocalDateTime observedAt) {
        if (observedAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int threshold = settings.getOnlineThresholdS();
        return Duration.between(observedAt, now).getSeconds() <= threshold;
    }

    private DeviceState parseStateJson(String stateJson) {
        if (stateJson == null || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.readValue(stateJson, DeviceState.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private record ShadowEntry(DeviceState state, LocalDateTime updatedAt, DeviceMeta meta) {
    }
}
