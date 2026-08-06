package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDate;
import java.util.List;

public record ZigbeePowerStatistics(
        String chartKind,
        String chartUnit,
        List<ZigbeeHistoryPoint> points,
        boolean energySupported,
        List<DailyUsage> daily
) {
    public record DailyUsage(
            LocalDate date,
            Long onDurationSeconds,
            Double energyKwh,
            boolean partial
    ) {
    }
}
