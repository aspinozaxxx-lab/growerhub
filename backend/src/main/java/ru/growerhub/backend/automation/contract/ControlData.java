package ru.growerhub.backend.automation.contract;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

public final class ControlData {
    private ControlData() {}
    public record Manual(UUID requestId, Object value, Integer ttlSeconds) {}
    public record Demand(UUID id, int revision, String sourceType, String sourceId, String sourceName, String reason,
            int priority, Object desiredValue, LocalDateTime requestedAt, LocalDateTime confirmedAt,
            LocalDateTime expiresAt, long remainingSeconds, String status, String error) {}
    public record Status(Integer resourceId, String sourceType, String sourceId, String sourceName, String reason,
            Integer priority, Object desiredValue, LocalDateTime requestedAt, LocalDateTime confirmedAt,
            LocalDateTime expiresAt, long remainingSeconds, String status, Object actualValue,
            LocalDateTime actualAt, boolean actualConfirmed, String error, List<Demand> demands) {}
    public record Step(UUID channelId, int revision, UUID commandId, AuthenticatedUser user,
            UUID coordinatorId, String ieeeAddress, String property, Object value) {}
}
