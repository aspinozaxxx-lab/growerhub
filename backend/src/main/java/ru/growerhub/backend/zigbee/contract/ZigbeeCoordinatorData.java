package ru.growerhub.backend.zigbee.contract;

public record ZigbeeCoordinatorData(
        String ieeeAddress,
        String friendlyName,
        Object data
) {
}
