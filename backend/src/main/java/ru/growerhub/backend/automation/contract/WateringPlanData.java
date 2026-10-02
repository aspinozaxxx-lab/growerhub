package ru.growerhub.backend.automation.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public final class WateringPlanData {
    private WateringPlanData() {}

    public record Point(LocalDateTime ts, Double value) {}

    public record Soil(
            @JsonProperty("current") Double current,
            @JsonProperty("observed_at") LocalDateTime observedAt,
            @JsonProperty("drying_fraction") Double dryingFraction,
            @JsonProperty("drying_per_hour") Double dryingPerHour,
            @JsonProperty("sample_count") int sampleCount,
            @JsonProperty("response_verified") boolean responseVerified,
            @JsonProperty("issue") String issue) {}

    public record Plan(
            @JsonProperty("version") int version,
            @JsonProperty("mode") String mode,
            @JsonProperty("status") String status,
            @JsonProperty("observe_only") boolean observeOnly,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("evaluated_at") LocalDateTime evaluatedAt,
            @JsonProperty("planned_at") LocalDateTime plannedAt,
            @JsonProperty("execution_key") String executionKey,
            @JsonProperty("due") boolean due,
            @JsonProperty("run_seconds") int runSeconds,
            @JsonProperty("pulse_enabled") boolean pulseEnabled,
            @JsonProperty("pulse_run_seconds") int pulseRunSeconds,
            @JsonProperty("pulse_pause_seconds") int pulsePauseSeconds,
            @JsonProperty("last_watered_at") LocalDateTime lastWateredAt,
            @JsonProperty("used_today_seconds") long usedTodaySeconds,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("soil") Soil soil,
            @JsonProperty("weather") WeatherForecastData.Decision weather,
            @JsonProperty("reasons") List<String> reasons) {}
}
