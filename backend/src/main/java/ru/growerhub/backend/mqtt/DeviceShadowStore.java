package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.mqtt.model.ManualWateringState;

@Component
public class DeviceShadowStore {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Map<String, ShadowEntry> storage = new ConcurrentHashMap<>();
    private final ManualWateringSettings settings;
    private final ObjectMapper objectMapper;

    public DeviceShadowStore(ManualWateringSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public void updateFromState(String deviceId, DeviceState state) {
        storage.put(deviceId, new ShadowEntry(state, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public DeviceState getLastState(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        return entry != null ? entry.state() : null;
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
        payload.put("state", objectMapper.convertValue(entry.state(), new TypeReference<Map<String, Object>>() {
        }));
        payload.put("updated_at", formatIsoUtc(entry.updatedAt()));
        return payload;
    }

    public Map<String, Object> getManualWateringView(String deviceId) {
        ShadowEntry entry = storage.get(deviceId);
        if (entry == null) {
            return null;
        }
        DeviceState state = entry.state();
        ManualWateringState manual = state.manualWatering();
        String status = manual != null ? manual.status() : null;
        Integer durationS = manual != null ? manual.durationS() : null;
        LocalDateTime startedAt = manual != null ? manual.startedAt() : null;
        String correlationId = manual != null ? manual.correlationId() : null;

        Integer remainingS = null;
        if ("running".equals(status) && durationS != null && startedAt != null) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            long elapsed = Duration.between(startedAt, now).getSeconds();
            remainingS = (int) Math.max(0, durationS - elapsed);
        }

        LocalDateTime observedAt = entry.updatedAt();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int threshold = settings.getDeviceOnlineThresholdSeconds();
        boolean isOnline = Duration.between(observedAt, now).getSeconds() <= threshold;
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
        view.put("source", "calculated");
        return view;
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
