package ru.growerhub.backend.device.contract;

public enum DeviceServiceEventType {
    SENSOR_READ_ERROR,
    DEVICE_REBOOT_SENSOR_FAILURE;

    public static DeviceServiceEventType fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeviceServiceEventType item : values()) {
            if (item.name().equalsIgnoreCase(value)) {
                return item;
            }
        }
        return null;
    }
}
