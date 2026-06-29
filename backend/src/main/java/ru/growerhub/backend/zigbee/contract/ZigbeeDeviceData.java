package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;
import java.util.List;

public record ZigbeeDeviceData(
        Integer id,
        String ieeeAddress,
        String friendlyName,
        String type,
        Boolean supported,
        Boolean disabled,
        boolean coordinator,
        Object bridgeDevice,
        Object definition,
        String imageUrl,
        List<ZigbeeFeatureData> features,
        List<ZigbeeFeatureData> metrics,
        List<ZigbeeFeatureData> controls,
        Object state,
        String availability,
        LocalDateTime lastStateAt,
        LocalDateTime updatedAt
) {
}
