package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeDeviceData(
        Integer id,
        String ieeeAddress,
        String friendlyName,
        String type,
        Boolean supported,
        Boolean disabled,
        boolean coordinator,
        Object bridgeDevice,
        Object state,
        String availability,
        LocalDateTime lastStateAt,
        LocalDateTime updatedAt
) {
}
