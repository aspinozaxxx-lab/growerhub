package ru.growerhub.backend.zigbee.contract;

import java.util.Set;

public record ZigbeeProductAnalytics(
        Set<Integer> usersWithCoordinator,
        Set<Integer> usersWithConnectedCoordinator,
        Set<Integer> usersWithFirstDevice,
        long coordinatorsCreated,
        long coordinatorsConnected,
        long activeCoordinators1d,
        long activeCoordinators7d,
        long activeCoordinators28d
) {
    public ZigbeeProductAnalytics {
        usersWithCoordinator = Set.copyOf(usersWithCoordinator);
        usersWithConnectedCoordinator = Set.copyOf(usersWithConnectedCoordinator);
        usersWithFirstDevice = Set.copyOf(usersWithFirstDevice);
    }
}
