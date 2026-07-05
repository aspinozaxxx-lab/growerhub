package ru.growerhub.backend.pump.contract;

import java.time.LocalDateTime;

public record PumpHistoryPoint(
        LocalDateTime ts,
        Double value,
        Boolean isRunning,
        String rawStatus
) {
}
