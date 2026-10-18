package ru.growerhub.backend.automation.contract;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
public final class RuleData {
    private RuleData() {}
    public record Definition(int version, String name, String scopeType, Integer scopeId, JsonNode trigger,
            JsonNode condition, List<JsonNode> actions, int cooldownSeconds) {}
    public record Save(Integer revision, boolean enabled, Definition definition) {}
    public record Toggle(int revision, boolean enabled) {}
    public record Restore(int revision, int sourceRevision) {}
    public record Rule(UUID id, int revision, boolean enabled, Definition definition, String description, LocalDateTime updatedAt, String reason) {}
    public record Preview(boolean valid, String description, List<String> conflicts, List<String> readiness, List<Integer> resources) {}
    public record Version(int revision, boolean enabled, Definition definition, LocalDateTime at) {}
    public record Run(UUID id, int revision, String status, int step, String reason, JsonNode result, LocalDateTime startedAt, LocalDateTime nextAt) {}
    public record Step(UUID runId, String lease, AuthenticatedUser user, JsonNode action, String bindingFingerprint) {}
    public record Command(int resourceId, String type, Object value, Integer durationSeconds) {}
}
