package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;
import java.util.List;

public final class ZigbeeWaterMeterData {
    private ZigbeeWaterMeterData() {}

    public record Observation(LocalDateTime receivedAt, LocalDateTime startedAt, LocalDateTime finishedAt,
                              Double volumeL, Double flowLMin, Double reportedDailyL, boolean complete,
                              String issue) {}

    public record History(boolean supported, boolean simulated, String label, Observation latest,
                          List<Observation> observations, boolean truncated) {}
}
