package ru.growerhub.backend.mqtt;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;

@Component
public class AckStore {
    private final Map<String, AckEntry> storage = new ConcurrentHashMap<>();

    public void put(String deviceId, ManualWateringAck ack) {
        storage.put(ack.correlationId(), new AckEntry(deviceId, ack, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public ManualWateringAck get(String correlationId) {
        AckEntry entry = storage.get(correlationId);
        return entry != null ? entry.ack() : null;
    }

    public void clear() {
        storage.clear();
    }

    private record AckEntry(String deviceId, ManualWateringAck ack, LocalDateTime insertedAt) {
    }
}
