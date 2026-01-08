package ru.growerhub.backend.device;

public final class DeviceDefaults {
    private DeviceDefaults() {
    }

    public static final double TARGET_MOISTURE = 40.0;
    public static final int WATERING_DURATION = 30;
    public static final int WATERING_TIMEOUT = 300;
    public static final int LIGHT_ON_HOUR = 6;
    public static final int LIGHT_OFF_HOUR = 22;
    public static final int LIGHT_DURATION = 16;
    public static final String CURRENT_VERSION = "1.0.0";
    public static final String LATEST_VERSION = "1.0.0";
    public static final boolean UPDATE_AVAILABLE = false;

    public static String defaultName(String deviceId) {
        return "Watering Device " + deviceId;
    }
}
