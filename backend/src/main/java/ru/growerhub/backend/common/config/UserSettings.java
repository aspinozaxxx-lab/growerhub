package ru.growerhub.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user")
public class UserSettings {
    private String defaultTimezone = "Europe/Moscow";
    private int pilotContactMaxLength = 254;
    private int pilotEquipmentMaxLength = 500;

    public int getPilotContactMaxLength() { return pilotContactMaxLength; }
    public void setPilotContactMaxLength(int value) { pilotContactMaxLength = value; }
    public int getPilotEquipmentMaxLength() { return pilotEquipmentMaxLength; }
    public void setPilotEquipmentMaxLength(int value) { pilotEquipmentMaxLength = value; }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }
}
