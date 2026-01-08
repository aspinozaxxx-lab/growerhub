package ru.growerhub.backend.device;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
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

    public DeviceShadowStore(
            DeviceSettings settings,
            ObjectMapper objectMapper,
            Clock clock,
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository
    ) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
    }

    public void updateFromState(String deviceId, DeviceState state) {
        updateFromState(deviceId, state, LocalDateTime.now(clock));
    }

    public void updateFromState(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        storage.put(deviceId, new ShadowEntry(state, updatedAt));
    }

    public void updateFromStateAndPersist(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        updateFromState(deviceId, state, updatedAt);
        if (deviceStateLastRepository == null) {
            return;
        }
        String payload = writeStateJson(state);
        DeviceStateLastEntity record = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        if (record == null) {
            record = DeviceStateLastEntity.create();
            record.setDeviceId(deviceId);
        }
        record.setStateJson(payload);
        record.setUpdatedAt(updatedAt);
        deviceStateLastRepository.save(record);
    }

    public DeviceState getLastState(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        return entry != null ? entry.state() : null;
    }

    public DeviceSnapshot getSnapshotOrLoad(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry != null && entry.updatedAt() != null) {
            return new DeviceSnapshot(entry.state(), entry.updatedAt(), isOnline(entry.updatedAt()), SnapshotSource.MEMORY);
        }
        DeviceSnapshot loaded = loadFromDb(deviceId);
        if (loaded == null) {
            return null;
        }
        storage.put(deviceId, new ShadowEntry(loaded.state(), loaded.updatedAt()));
        return loaded;
    }

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
        return new DeviceSnapshot(state, updatedAt, isOnline(updatedAt), source);
    }

    public void clear() {
        storage.clear();
    }

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
        payload.put("updated_at", formatIsoUtc(entry.updatedAt()));
        return payload;
    }

    public Map<String, Object> getManualWateringView(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry == null || entry.state() == null) {
            return null;
        }
        return buildManualWateringView(entry.state(), entry.updatedAt(), "calculated");
    }

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

    public record DeviceSnapshot(DeviceState state, LocalDateTime updatedAt, boolean isOnline, SnapshotSource source) {
    }

    public enum SnapshotSource {
        MEMORY,
        DB_STATE,
        DB_FALLBACK
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

    private String writeStateJson(DeviceState state) {
        if (objectMapper == null) {
            throw new IllegalStateException("objectMapper ne naiden");
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String formatIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).withNano(0).format(ISO_UTC);
    }

    private record ShadowEntry(DeviceState state, LocalDateTime updatedAt) {
    }
}
