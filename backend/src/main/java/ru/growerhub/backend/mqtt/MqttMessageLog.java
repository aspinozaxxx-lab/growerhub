package ru.growerhub.backend.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageLog {
    private final Deque<MqttRecordedMessage> messages = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private final MqttSettings settings;
    private final Clock clock;

    public MqttMessageLog(MqttSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public void recordInbound(String topic, byte[] payload, String kind) {
        record("in", topic, resolveSender(topic, null), kind, payload);
    }

    public void recordOutbound(String topic, byte[] payload) {
        record("out", topic, "backend", "cmd", payload);
    }

    public synchronized List<MqttRecordedMessage> list(String topicFilter, String senderFilter, Integer limit) {
        String normalizedTopic = normalizeFilter(topicFilter);
        String normalizedSender = normalizeFilter(senderFilter);
        int resolvedLimit = resolveLimit(limit);
        List<MqttRecordedMessage> result = new ArrayList<>();
        for (MqttRecordedMessage message : messages) {
            if (!matches(message.topic(), normalizedTopic)) {
                continue;
            }
            if (!matches(message.sender(), normalizedSender)) {
                continue;
            }
            result.add(message);
            if (result.size() >= resolvedLimit) {
                break;
            }
        }
        return result;
    }

    public synchronized void clear() {
        messages.clear();
    }

    private synchronized void record(String direction, String topic, String sender, String kind, byte[] payload) {
        int capacity = Math.max(1, settings.getRecentMessagesLimit());
        messages.addFirst(new MqttRecordedMessage(
                sequence.incrementAndGet(),
                LocalDateTime.now(clock),
                direction,
                topic,
                sender,
                kind,
                safePayload(payload)
        ));
        while (messages.size() > capacity) {
            messages.removeLast();
        }
    }

    private int resolveLimit(Integer limit) {
        int capacity = Math.max(1, settings.getRecentMessagesLimit());
        if (limit == null || limit <= 0) {
            return capacity;
        }
        return Math.min(limit, capacity);
    }

    private String safePayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        String text = new String(payload, StandardCharsets.UTF_8);
        int maxChars = Math.max(1, settings.getRecentPayloadChars());
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private String resolveSender(String topic, String fallback) {
        if (topic == null) {
            return fallback;
        }
        String[] parts = topic.split("/");
        if (parts.length >= 3 && "gh".equals(parts[0]) && "dev".equals(parts[1]) && !parts[2].isBlank()) {
            return parts[2];
        }
        return fallback;
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String value, String filter) {
        if (filter == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(filter);
    }
}
