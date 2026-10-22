package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeControlObservation(Object value, LocalDateTime observedAt, boolean online) {}
