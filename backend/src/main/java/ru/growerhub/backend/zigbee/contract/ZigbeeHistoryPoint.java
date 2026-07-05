package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;

public record ZigbeeHistoryPoint(
        LocalDateTime ts,
        String property,
        Double value,
        String rawValue,
        String valueText,
        Boolean valueBoolean
) {
}
