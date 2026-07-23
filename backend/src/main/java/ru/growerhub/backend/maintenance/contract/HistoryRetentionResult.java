package ru.growerhub.backend.maintenance.contract;

import java.time.LocalDate;

public record HistoryRetentionResult(
        LocalDate day,
        int sensorRowsDeleted,
        int plantRowsDeleted,
        int pumpRowsDeleted,
        int zigbeeRowsDeleted,
        boolean caughtUp
) {
    public int totalRowsDeleted() {
        return sensorRowsDeleted + plantRowsDeleted + pumpRowsDeleted + zigbeeRowsDeleted;
    }

    public static HistoryRetentionResult noWork(boolean caughtUp) {
        return new HistoryRetentionResult(null, 0, 0, 0, 0, caughtUp);
    }
}
