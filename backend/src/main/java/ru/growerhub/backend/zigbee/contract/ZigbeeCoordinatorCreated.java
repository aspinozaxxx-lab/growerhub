package ru.growerhub.backend.zigbee.contract;

public record ZigbeeCoordinatorCreated(
        ZigbeeCoordinatorSummary coordinator,
        ZigbeeCoordinatorSetup setup
) {
}
