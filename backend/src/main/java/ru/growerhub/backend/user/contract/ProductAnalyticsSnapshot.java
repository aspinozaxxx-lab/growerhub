package ru.growerhub.backend.user.contract;

import java.util.Set;

public record ProductAnalyticsSnapshot(Set<Integer> registeredUserIds) {
    public ProductAnalyticsSnapshot {
        registeredUserIds = Set.copyOf(registeredUserIds);
    }
}
