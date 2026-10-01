package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zigbee")
public class ZigbeeSettings {
    private int permitJoinDefaultSeconds = 254;
    private int friendlyNameMaxChars = 255;

    public int getFriendlyNameMaxChars() {
        return friendlyNameMaxChars;
    }

    public void setFriendlyNameMaxChars(int friendlyNameMaxChars) {
        this.friendlyNameMaxChars = friendlyNameMaxChars;
    }

    public int getPermitJoinDefaultSeconds() {
        return permitJoinDefaultSeconds;
    }

    public void setPermitJoinDefaultSeconds(int permitJoinDefaultSeconds) {
        this.permitJoinDefaultSeconds = permitJoinDefaultSeconds;
    }
}
