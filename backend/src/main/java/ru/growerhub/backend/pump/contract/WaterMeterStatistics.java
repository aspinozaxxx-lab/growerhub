package ru.growerhub.backend.pump.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WaterMeterStatistics(
        boolean supported, boolean simulated, String label, String month, String timezone, String source, LocalDate today,
        @JsonProperty("known_volume_l") Double knownVolumeL,
        @JsonProperty("partial_volume") boolean partialVolume,
        @JsonProperty("history_truncated") boolean historyTruncated,
        @JsonProperty("flow_l_min") Double flowLMin,
        @JsonProperty("flow_observed_at") LocalDateTime flowObservedAt,
        @JsonProperty("reported_daily_l") Double reportedDailyL,
        @JsonProperty("daily_counter_verified") boolean dailyCounterVerified,
        @JsonProperty("allocation_rule") String allocationRule,
        List<Day> days, List<Operation> operations) {

    public record Day(LocalDate date, @JsonProperty("known_volume_l") Double knownVolumeL,
                      @JsonProperty("operation_count") int operationCount,
                      @JsonProperty("partial_volume") boolean partialVolume) {}

    public record Operation(@JsonProperty("started_at") LocalDateTime startedAt,
                            @JsonProperty("finished_at") LocalDateTime finishedAt,
                            @JsonProperty("first_observed_at") LocalDateTime firstObservedAt,
                            @JsonProperty("volume_l") Double volumeL, boolean imported, String issue) {}
}
