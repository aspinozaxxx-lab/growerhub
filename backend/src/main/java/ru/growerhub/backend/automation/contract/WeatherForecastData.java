package ru.growerhub.backend.automation.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

public final class WeatherForecastData {
    private WeatherForecastData() {}

    public record Location(Double latitude, Double longitude, String label) {}
    public record Period(LocalDateTime from, LocalDateTime to, double precipitationMm,
            Double probability, String symbol) {}
    public record Forecast(String source, LocalDateTime updatedAt, LocalDateTime retrievedAt,
            List<Period> periods, String issue) {}
    public record Pending(String fingerprint, String key, LocalDateTime originalAt, LocalDateTime deadline) {}
    public record Input(Forecast forecast, Pending pending, String fingerprint) {}

    public record Decision(String source, String status,
            @JsonProperty("updated_at") LocalDateTime updatedAt,
            @JsonProperty("retrieved_at") LocalDateTime retrievedAt,
            @JsonProperty("from") LocalDateTime from, @JsonProperty("to") LocalDateTime to,
            @JsonProperty("expected_mm") Double expectedMm,
            @JsonProperty("max_period_probability") Double maxPeriodProbability,
            @JsonProperty("unavailable_policy") String unavailablePolicy,
            Pending pending, String issue) {}
}
