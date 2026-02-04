package ru.growerhub.backend.device.engine;

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
import ru.growerhub.backend.common.config.DeviceSettings;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.diagnostics.PlantTiming;

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

    public void updateFromState(String deviceId, DeviceShadowState state) {
        updateFromState(deviceId, state, LocalDateTime.now(clock));
    }

    public void updateFromState(String deviceId, DeviceShadowState state, LocalDateTime updatedAt) {
        DeviceShadowState merged = mergeManualWateringState(deviceId, state);
        storage.put(deviceId, new ShadowEntry(merged, updatedAt));
    }

    public void remove(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        storage.remove(deviceId);
    }

    public void updateFromStateAndPersist(String deviceId, DeviceShadowState state, LocalDateTime updatedAt) {
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

    public DeviceShadowState getLastState(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        return entry != null ? entry.state() : null;
    }

    public DeviceSnapshot getSnapshotOrLoad(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry != null && entry.updatedAt() != null) {
            PlantTiming.recordShadowHit();
            return new DeviceSnapshot(entry.state(), entry.updatedAt(), isOnline(entry.updatedAt()), SnapshotSource.MEMORY);
        }
        PlantTiming.recordShadowMiss();
        long loadStart = PlantTiming.startTimer();
        DeviceSnapshot loaded = loadFromDb(deviceId);
        PlantTiming.recordShadowLoad(loadStart);
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
        DeviceShadowState state = null;
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

    public Map<String, Object> buildManualWateringView(DeviceShadowState state, LocalDateTime observedAt, String source) {
        if (state == null) {
            return null;
        }
        DeviceShadowState.ManualWateringState manual = state.manualWatering();
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

    public record DeviceSnapshot(DeviceShadowState state, LocalDateTime updatedAt, boolean isOnline, SnapshotSource source) {
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

    private DeviceShadowState parseStateJson(String stateJson) {
        if (stateJson == null || objectMapper == null) {
            return null;
        }
        long start = PlantTiming.startTimer();
        try {
            return objectMapper.readValue(stateJson, DeviceShadowState.class);
        } catch (Exception ex) {
            return null;
        } finally {
            PlantTiming.recordShadowJson(start);
        }
    }

    private String writeStateJson(DeviceShadowState state) {
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

    private DeviceShadowState mergeManualWateringState(String deviceId, DeviceShadowState next) {
        if (next == null) {
            return null;
        }
        DeviceShadowState.ManualWateringState current = next.manualWatering();
        if (current == null) {
            return next;
        }
        ShadowEntry prevEntry = storage.get(deviceId);
        DeviceShadowState prevState = prevEntry != null ? prevEntry.state() : null;
        DeviceShadowState.ManualWateringState prev = prevState != null ? prevState.manualWatering() : null;
        if (prev == null) {
            return next;
        }
        String correlationId = preferNonBlank(current.correlationId(), prev.correlationId());
        boolean sameCorrelation = correlationId != null && correlationId.equals(prev.correlationId());
        if (!sameCorrelation) {
            return next;
        }
        String currentJournal = current.journalWrittenForCorrelationId();
        String prevJournal = prev.journalWrittenForCorrelationId();
        String journalWritten = preferNonBlank(currentJournal, prevJournal);
        boolean alreadyWritten = journalWritten != null && journalWritten.equals(correlationId);
        String status = preferNonBlank(current.status(), prev.status());
        if (alreadyWritten && (currentJournal == null || currentJournal.isBlank())) {
            if (prev.status() != null && !prev.status().isBlank()) {
                status = prev.status();
            }
        }
        DeviceShadowState.ManualWateringState mergedManual = new DeviceShadowState.ManualWateringState(
                status,
                preferNonNull(current.durationS(), prev.durationS()),
                preferNonNull(current.startedAt(), prev.startedAt()),
                preferNonNull(current.remainingS(), prev.remainingS()),
                correlationId,
                preferNonNull(current.waterVolumeL(), prev.waterVolumeL()),
                preferNonNull(current.ph(), prev.ph()),
                preferNonBlank(current.fertilizersPerLiter(), prev.fertilizersPerLiter()),
                journalWritten
        );
        return new DeviceShadowState(
                mergedManual,
                next.fwVer(),
                next.soilMoisture(),
                next.airTemperature(),
                next.airHumidity(),
                next.air(),
                next.soil(),
                next.light(),
                next.pump(),
                next.scenarios()
        );
    }

    private <T> T preferNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private String preferNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record ShadowEntry(DeviceShadowState state, LocalDateTime updatedAt) {
    }
}
