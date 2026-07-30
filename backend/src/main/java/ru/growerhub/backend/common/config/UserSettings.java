package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user")
public class UserSettings {
    private String defaultTimezone = "Europe/Moscow";

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }
}
