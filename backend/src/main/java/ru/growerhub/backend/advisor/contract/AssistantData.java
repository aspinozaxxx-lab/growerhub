package ru.growerhub.backend.advisor.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

public final class AssistantData {
    private AssistantData() {}
    public record Origin(String address, String visitor, String channel) {}
    public record Conversation(UUID id, String title, String model, String effort, LocalDateTime updatedAt) {}
    public record Options(String model, String effort) {}
    public record Send(UUID clientKey, String text, List<UUID> attachments) {}
    public record Message(UUID id, String text, String answer, String status, String error,
            String model, String effort, List<UUID> attachments, List<Proposal> proposals, LocalDateTime createdAt) {}
    public record Proposal(UUID id, String title, String kind, JsonNode payload, String status,
            String revision, LocalDateTime expiresAt, JsonNode result) {}
    public record Confirm(String revision) {}
    public record Attachment(UUID id, String mime, long size) {}
    public record Image(byte[] bytes, String mime) {}
    public record PhotoPermit(UUID id, int maxBytes, long maxPixels, int edge) {}
    public record Model(String id, String name, List<String> efforts, String defaultEffort, boolean images, boolean preferred) {}
    public record RuntimeStatus(String installed, String latest, java.time.Instant checkedAt, boolean updateAvailable, String error) {}
    public record Capabilities(boolean enabled, boolean available, boolean owner, boolean demo,
            String model, String effort, List<Model> models, LimitsView limits, Preferences preferences) {}
    public record LimitsView(int perMinute, int perDay, long remainingDay, String reason) {}
    public record Preferences(boolean telegram, String report, int hour, int weekday, String conversationId) {}
    public record Work(UUID id, UUID conversationId, String lease, AuthenticatedUser user, String provider,
            String model, String effort, List<Map<String, Object>> messages, List<Map<String, Object>> tools,
            int maxInputTokens, int maxOutputTokens, int maxModelCalls, int maxToolCalls, long maxCostMicros) {}
    public record ToolCall(String id, String name, JsonNode arguments) {}
    public record ToolPermit(boolean execute, JsonNode result) {}
    public record Application(UUID id, AuthenticatedUser user) {}
    public record Outcome(String status, String text, long inputTokens, long outputTokens, boolean usageKnown, String code) {}
    public record Usage(boolean publicEnabled, long monthMicros, long dayMicros, long hourMicros,
            long reservedMicros, long requests, long rejected, Map<String, Long> statuses,
            long monthLimitMicros, long dayLimitMicros, long hourLimitMicros, Map<String, Long> denials) {}
    public record Switch(boolean enabled) {}
    public record ReportDue(Integer userId, String key) {}
    public record Incoming(ru.growerhub.backend.notification.contract.TelegramData.Inbox item, AuthenticatedUser user, UUID conversation, PhotoPermit photo) {}
}
