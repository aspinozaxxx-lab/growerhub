package ru.growerhub.backend.pump.engine;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.zigbee.contract.ZigbeeWaterMeterData;

class WaterMeterStatisticsServiceTest {
    private final LocalDateTime from = LocalDateTime.parse("2026-09-30T21:00:00");
    private final LocalDateTime to = LocalDateTime.parse("2026-10-31T21:00:00");

    @Test
    void repeatsCachedLastWateringOnceAndUsesOperationEndLocalDate() {
        var start = LocalDateTime.parse("2026-09-30T20:50:00");
        var end = LocalDateTime.parse("2026-09-30T21:10:00");
        var first = report(start, end, 42.0, LocalDateTime.parse("2026-10-01T09:00:00"));
        var repeat = report(start, end, 42.0, LocalDateTime.parse("2026-10-02T09:00:00"));
        var result = summarize(first, repeat);
        assertThat(result.knownVolumeL()).isEqualTo(42);
        assertThat(result.operations()).hasSize(1);
        assertThat(result.operations().getFirst().imported()).isTrue();
        assertThat(result.days().getFirst().date().toString()).isEqualTo("2026-10-01");
        assertThat(result.days().getFirst().knownVolumeL()).isEqualTo(42);
        assertThat(result.days().get(1).knownVolumeL()).isNull();
        assertThat(result.partialVolume()).isTrue();
        assertThat(result.dailyCounterVerified()).isFalse();
        assertThat(result.reportedDailyL()).isEqualTo(235);
    }

    @Test
    void inconsistentVolumeIsUnknownInsteadOfSummingOrTakingLargestReport() {
        var start = LocalDateTime.parse("2026-10-01T07:00:00");
        var end = start.plusMinutes(5);
        var result = summarize(report(start, end, 42.0, end.plusSeconds(1)), report(start, end, 50.0, end.plusMinutes(5)));
        assertThat(result.knownVolumeL()).isNull();
        assertThat(result.operations()).hasSize(1);
        assertThat(result.operations().getFirst().issue()).isEqualTo("inconsistent_operation_reports");
    }

    @Test
    void previousMonthCachedReportIsNotNewWateringToday() {
        var start = LocalDateTime.parse("2026-09-29T07:00:00");
        var result = summarize(report(start, start.plusMinutes(5), 42.0, from.plusDays(1)));
        assertThat(result.operations()).isEmpty();
        assertThat(result.knownVolumeL()).isNull();
    }

    private ru.growerhub.backend.pump.contract.WaterMeterStatistics summarize(ZigbeeWaterMeterData.Observation... reports) {
        var history = new ZigbeeWaterMeterData.History(true, false, "SWV", reports[reports.length - 1], List.of(reports), false);
        return WaterMeterStatisticsService.summarize(history, YearMonth.of(2026, 10), ZoneId.of("Europe/Istanbul"), from, to,
                java.time.LocalDate.of(2026, 10, 2));
    }

    private ZigbeeWaterMeterData.Observation report(LocalDateTime start, LocalDateTime end, Double liters, LocalDateTime received) {
        return new ZigbeeWaterMeterData.Observation(received, start, end, liters, 0.0, 235.0, true, null);
    }
}
