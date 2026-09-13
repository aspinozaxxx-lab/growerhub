package ru.growerhub.backend.demo.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DemoData {
    private DemoData() {}
    public record Space(UUID id, @JsonIgnore Integer dataUserId, @JsonIgnore int generation,
            boolean saved, String locale, String timezone, @JsonProperty("expires_at") LocalDateTime expiresAt) {}
    public record Status(UUID id, boolean saved, String locale, String timezone,
            @JsonProperty("expires_at") LocalDateTime expiresAt, List<Device> devices) {}
    public record Device(UUID id, String profile, String name, Map<String, Object> state) {}
    public record Profile(String key, String name, String description) {}
    public record Tick(Integer ownerId, boolean telemetry) {}
    public record AddDevice(String profile, String name) {}
    public record Environment(@JsonProperty("device_id") UUID deviceId, Double temperature, Double moisture, Boolean leak) {}
}
