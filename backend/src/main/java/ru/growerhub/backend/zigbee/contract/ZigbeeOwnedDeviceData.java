package ru.growerhub.backend.zigbee.contract;

import java.util.UUID;

public record ZigbeeOwnedDeviceData(
        Integer coordinatorInternalId,
        UUID coordinatorId,
        String coordinatorName,
        ZigbeeDeviceData device
) {
}
