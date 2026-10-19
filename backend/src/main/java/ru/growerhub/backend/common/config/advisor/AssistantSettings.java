package ru.growerhub.backend.common.config.advisor;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantSettings(boolean enabled, boolean publicEnabled, Integer ownerUserId,
        String ownerUrl, String publicUrl, String ownerToken, String publicToken, String hmacSecret,
        Set<String> trustedProxies, String publicModel, String publicEffort, Budget budget, Limits limits,
        int workerDelayMs, int retentionDays, int contextMessages, int maxToolResultChars, int proposalMinutes,
        int reportHour, String siteUrl, int schedulerThreads, Maintenance maintenance, int historyPoints, int requestsPerDay,
        String interfaceGuide) {
    public record Maintenance(String url, String token, int timeoutSeconds) {
        public boolean enabled() { return token != null && token.length() >= 32; }
    }
    public record Budget(long monthMicros, long dayMicros, long hourMicros, long turnMicros,
            long userDayMicros, long guestsDayMicros, long inputNanoUsd, long outputNanoUsd) {}
    public record Limits(int accountMinute, int accountDay, int guestMinute, int guestDay, int ownerMinute,
            int ownerDay, int ipMinute, int ipDay, int publicConcurrency, int ownerConcurrency, int queueSize,
            int queueSeconds, int leaseSeconds, int ownerLeaseSeconds, int maxTextChars, int maxPhotos,
            int maxPhotoBytes, long maxImagePixels, int imageEdge, long accountPhotoBytes, long guestPhotoBytes, long totalPhotoBytes,
            int uploadMinute, int conversationsDay, int maxInputTokens, int maxOutputTokens,
            int maxModelCalls, int maxToolCalls, int maxResponseChars, int streams, int streamSeconds) {}
}
