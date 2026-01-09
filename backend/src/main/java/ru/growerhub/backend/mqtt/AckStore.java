package ru.growerhub.backend.mqtt;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.device.AckSettings;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;

@Component
public class AckStore {
    private final Map<String, AckEntry> storage = new ConcurrentHashMap<>();
    private final AckSettings ackSettings;
    private final Clock clock;

    public AckStore(AckSettings ackSettings, Clock clock) {
        this.ackSettings = ackSettings;
        this.clock = clock;
    }

    public void put(String deviceId, ManualWateringAck ack) {
        storage.put(ack.correlationId(), new AckEntry(deviceId, ack, LocalDateTime.now(clock)));
    }

    public ManualWateringAck get(String correlationId) {
        AckEntry entry = storage.get(correlationId);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry, LocalDateTime.now(clock))) {
            storage.remove(correlationId);
            return null;
        }
        return entry.ack();
    }

    public void clear() {
        storage.clear();
    }

    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        int removed = 0;
        for (Map.Entry<String, AckEntry> entry : storage.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                storage.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    private boolean isExpired(AckEntry entry, LocalDateTime now) {
        int ttlSeconds = ackSettings.getTtlSeconds();
        if (ttlSeconds <= 0) {
            return false;
        }
        LocalDateTime expiresAt = entry.insertedAt().plusSeconds(ttlSeconds);
        return !expiresAt.isAfter(now);
    }

    private record AckEntry(String deviceId, ManualWateringAck ack, LocalDateTime insertedAt) {
    }
}
