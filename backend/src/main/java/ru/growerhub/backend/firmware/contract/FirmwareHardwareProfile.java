package ru.growerhub.backend.firmware.contract;

import java.util.Locale;

public enum FirmwareHardwareProfile {
    ESP32DEV("esp32dev"),
    ESP32C3_SUPERMINI("esp32c3_supermini");

    private final String value;

    FirmwareHardwareProfile(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String filename(String version) {
        return version + "." + value + ".bin";
    }

    public static FirmwareHardwareProfile fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FirmwareHardwareProfile profile : values()) {
            if (profile.value.equals(normalized)) {
                return profile;
            }
        }
        return null;
    }
}
