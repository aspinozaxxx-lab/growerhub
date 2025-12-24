package ru.growerhub.backend.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ack")
public class AckSettings {
    private int ttlSeconds = 180;
    private int cleanupPeriodSeconds = 60;

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getCleanupPeriodSeconds() {
        return cleanupPeriodSeconds;
    }

    public void setCleanupPeriodSeconds(int cleanupPeriodSeconds) {
        this.cleanupPeriodSeconds = cleanupPeriodSeconds;
    }

}
