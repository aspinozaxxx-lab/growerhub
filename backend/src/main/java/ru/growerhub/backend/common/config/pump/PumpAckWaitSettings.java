package ru.growerhub.backend.common.config.pump;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki ozhidaniya ACK dlya poliva.
@ConfigurationProperties(prefix = "pump.ack.wait")
public class PumpAckWaitSettings {
    private int defaultTimeoutSeconds = 5;
    private int maxTimeoutSeconds = 15;
    private int pollIntervalMs = 500;

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }
}
