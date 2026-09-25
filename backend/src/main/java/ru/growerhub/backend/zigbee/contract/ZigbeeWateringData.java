package ru.growerhub.backend.zigbee.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class ZigbeeWateringData {
    private ZigbeeWateringData() {}

    public record Target(
            @JsonProperty("coordinator_id") Integer coordinatorId,
            @JsonProperty("ieee_address") String ieeeAddress,
            @JsonProperty("property") String property
    ) {
        public String key() { return "zigbee:" + coordinatorId + ":" + ieeeAddress + ":" + property; }
    }

    public record Capability(
            @JsonProperty("property") String property,
            @JsonProperty("ready") boolean ready,
            @JsonProperty("reason") String reason,
            @JsonProperty("max_duration_s") Integer maxDurationS,
            @JsonProperty("pulse") boolean pulse,
            @JsonProperty("until_leak") boolean untilLeak
    ) {}

    public record Executor(Target target, Integer ownerId, boolean simulated, String label,
                           String onValue, String offValue, Capability capability) {}

    public record State(boolean online, Boolean running, LocalDateTime observedAt) {}
}
