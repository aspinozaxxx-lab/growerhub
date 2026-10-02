package ru.growerhub.backend.automation.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.automation.contract.WateringPlanData;
import ru.growerhub.backend.common.config.automation.WateringPlanSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.pump.contract.PumpSessionData;

class WateringPlanEngineTest {
    private final WateringPlanEngine engine = new WateringPlanEngine(new WateringPlanSettings(
            90, 168, 8, 4, 120, 1, 0.5, 0.001, 45, 6, 0.05, 6, 1200, "07:00", "10:00"), new ru.growerhub.backend.common.config.automation.WeatherSettings("https://example.test", "GrowerHub", 15, 2000000, 64, 1500, 3600, 12, 6, 2, 24, 72, 2, 240, 1, false, "roof", "pause", "sunny"));

    private Map<String, Object> config(String mode) {
        var cfg = new LinkedHashMap<>(engine.defaults());
        cfg.putAll(Map.of("trigger_mode", mode, "observe_only", false, "run_seconds", 30,
                "stop_mode", "fixed_duration", "pulse_enabled", false,
                "pulse_run_minutes", 1, "pulse_pause_minutes", 1));
        return cfg;
    }

    @Test
    void scheduleUsesLocalDaysWithoutSoilAndMissedSlotsAreNotCaughtUp() {
        var cfg = config("schedule"); cfg.put("schedule_days", List.of(5));
        var now = LocalDateTime.parse("2026-10-02T04:00:30");
        var due = engine.evaluate(10, cfg, now, "Europe/Istanbul", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(due.due()).isTrue();
        assertThat(due.soil()).isNull();
        assertThat(due.plannedAt()).isEqualTo(LocalDateTime.parse("2026-10-02T04:00:00"));
        var consumed = engine.evaluate(10, cfg, now, "Europe/Istanbul", true, null, null, List.of(), null, 0, false, due.executionKey(), null);
        assertThat(consumed.due()).isFalse();
        assertThat(consumed.plannedAt()).isEqualTo(LocalDateTime.parse("2026-10-09T04:00:00"));
        var late = engine.evaluate(10, cfg, now.plusMinutes(5), "Europe/Istanbul", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(late.due()).isFalse();
        assertThat(late.plannedAt()).isEqualTo(consumed.plannedAt());
    }

    @Test
    void observationDisabledEquipmentAndLimitsNeverProduceCommands() {
        var cfg = config("schedule");
        var now = LocalDateTime.parse("2026-10-02T07:00:00");
        cfg.put("observe_only", true);
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, null).due()).isFalse();
        cfg.put("observe_only", false);
        assertThat(engine.evaluate(1, cfg, now, "UTC", false, null, null, List.of(), null, 0, false, null, null).due()).isFalse();
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, "Нет связи", null, List.of(), null, 0, false, null, null).status()).isEqualTo("unready");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 1190, false, null, null).status()).isEqualTo("limited");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, true, null, null).due()).isFalse();
    }

    @Test
    void daylightSavingGapSkipsSlotAndOverlapUsesOneLocalExecutionKey() {
        var cfg = config("schedule"); cfg.put("schedule_time", "02:30");
        var gap = engine.evaluate(1, cfg, LocalDateTime.parse("2026-03-29T00:00:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(gap.plannedAt()).isEqualTo(LocalDateTime.parse("2026-03-30T00:30:00"));
        var first = engine.evaluate(1, cfg, LocalDateTime.parse("2026-10-25T00:30:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(first.due()).isTrue();
        var second = engine.evaluate(1, cfg, LocalDateTime.parse("2026-10-25T01:30:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, first.executionKey(), null);
        assertThat(second.due()).isFalse();
    }

    @Test
    void localOrientationWorksBothWaysAndRequiresRealWateringResponse() {
        var now = LocalDateTime.parse("2026-10-02T12:00:00");
        var last = mock(PumpSessionData.View.class);
        when(last.id()).thenReturn(20L); when(last.phase()).thenReturn("completed"); when(last.activeDurationS()).thenReturn(30);
        when(last.startedAt()).thenReturn(now.minusHours(16).minusMinutes(1)); when(last.finishedAt()).thenReturn(now.minusHours(16));
        var history = responseHistory(now);
        var cfg = config("drying");
        cfg.putAll(Map.of("wet_anchor", 70, "dry_anchor", 35, "calibration_binding_key", "sensor:1", "window_start", "00:00", "window_end", "00:00"));
        var plan = engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", history, last, 0, false, null, null);
        assertThat(plan.soil().responseVerified()).isTrue();
        assertThat(plan.soil().dryingFraction()).isGreaterThanOrEqualTo(1);
        assertThat(plan.due()).isTrue();
        cfg.put("wet_anchor", 30); cfg.put("dry_anchor", 65);
        var reverse = history.stream().map(p -> new WateringPlanData.Point(p.ts(), 100 - p.value())).toList();
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", reverse, last, 0, false, null, null).due()).isTrue();
        var moved = engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:2", reverse, last, 0, false, null, null);
        assertThat(moved.due()).isFalse();
        assertThat(moved.soil().issue()).contains("ориентиры");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", reverse, null, 0, false, null, null).due()).isFalse();
    }

    @Test
    void staleJumpAndFlatHistoryAreNotDrynessTriggers() {
        var now = LocalDateTime.parse("2026-10-02T12:00:00");
        var cfg = config("drying"); cfg.putAll(Map.of("wet_anchor", 70, "dry_anchor", 35, "calibration_binding_key", "sensor:1"));
        var points = new ArrayList<WateringPlanData.Point>();
        for (int i = 0; i < 12; i++) points.add(new WateringPlanData.Point(now.minusHours(11 - i), 30.0));
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", points, null, 0, false, null, null).due()).isFalse();
        points.set(11, new WateringPlanData.Point(now, 90.0));
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", points, null, 0, false, null, null).soil().issue()).contains("скачок");
        assertThat(engine.evaluate(1, cfg, now.plusHours(3), "UTC", true, null, "sensor:1", points, null, 0, false, null, null).soil().issue()).contains("устарели");
    }

    @Test
    void invalidLimitsAndUnknownModesAreRejected() {
        var cfg = config("schedule"); cfg.put("run_seconds", 1500);
        assertThatThrownBy(() -> engine.validate(cfg)).isInstanceOf(DomainException.class);
        var unknown = config("flood");
        assertThatThrownBy(() -> engine.validate(unknown)).isInstanceOf(DomainException.class);
    }

    private List<WateringPlanData.Point> responseHistory(LocalDateTime now) {
        List<WateringPlanData.Point> points = new ArrayList<>();
        for (int i = 0; i < 8; i++) points.add(new WateringPlanData.Point(now.minusHours(20).plusMinutes(i * 30), 36.0));
        for (int i = 0; i < 30; i++) points.add(new WateringPlanData.Point(now.minusHours(15).plusMinutes(i * 30), 70.0 - i * 1.3));
        points.add(new WateringPlanData.Point(now, 31.0));
        return points;
    }

    @Test
    void rainHoldKeepsOriginalSlotAcrossRestartAndClearingRainReleasesItOnce() {
        var cfg = config("schedule"); cfg.putAll(Map.of("weather_enabled", true, "rain_exposure", "outdoors"));
        var now = LocalDateTime.parse("2026-10-03T07:00:30");
        var rain = weather(now, 1, null, "rain", null, "config1");
        var plan = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, rain);
        assertThat(plan.due()).isFalse(); assertThat(plan.status()).isEqualTo("weather_postponed");
        assertThat(plan.weather().expectedMm()).isEqualTo(6);
        assertThat(plan.weather().maxPeriodProbability()).isNull();
        var pending = plan.weather().pending();
        var later = now.plusHours(2);
        var waiting = engine.evaluate(1, cfg, later, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(later, 1, 60.0, "rain", pending, "config1"));
        assertThat(waiting.executionKey()).isEqualTo(plan.executionKey());
        assertThat(waiting.weather().pending().deadline()).isEqualTo(pending.deadline());
        var clear = engine.evaluate(1, cfg, later, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(later, 0, null, "clearsky_day", pending, "config1"));
        assertThat(clear.due()).isTrue(); assertThat(clear.executionKey()).isEqualTo(plan.executionKey());
        var repeated = engine.evaluate(1, cfg, later, "UTC", true, null, null, List.of(), null, 0, false, clear.executionKey(),
                weather(later, 0, null, "clearsky_day", pending, "config1"));
        assertThat(repeated.due()).isFalse();
        assertThat(repeated.executionKey()).isNotEqualTo(clear.executionKey());
    }

    @Test
    void roofUnavailableRuleStaleModelSnowAndEquipmentAreExplicit() {
        var cfg = config("schedule"); cfg.putAll(Map.of("weather_enabled", true, "rain_exposure", "roof"));
        var now = LocalDateTime.parse("2026-10-03T07:00:00");
        var roof = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(roof.due()).isTrue(); assertThat(roof.weather().status()).isEqualTo("roof");
        cfg.put("rain_exposure", "outdoors");
        var unavailable = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(unavailable.due()).isFalse(); assertThat(unavailable.weather().status()).isEqualTo("unavailable");
        cfg.put("weather_unavailable_policy", "base");
        var fallback = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, null);
        assertThat(fallback.due()).isTrue(); assertThat(fallback.weather().status()).isEqualTo("fallback");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, "Нет связи", null, List.of(), null, 0, false, null, null).due()).isFalse();
        cfg.put("weather_unavailable_policy", "pause");
        var snow = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(now, 2, 100.0, "snow", null, "c"));
        assertThat(snow.due()).isFalse(); assertThat(snow.weather().issue()).contains("снег");
        var staleInput = weather(now.minusHours(13), 0, null, "clearsky", null, "c");
        var stale = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null, staleInput);
        assertThat(stale.due()).isFalse(); assertThat(stale.weather().issue()).contains("устарела");
    }

    @Test
    void expiredHoldAndChangedConfigNeverCatchUpAndRainDoesNotBypassLimits() {
        var cfg = config("schedule"); cfg.putAll(Map.of("weather_enabled", true, "rain_exposure", "outdoors", "rain_max_delay_hours", 1));
        var now = LocalDateTime.parse("2026-10-03T07:00:30");
        var first = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(now, 1, null, "rain", null, "c1"));
        var pending = first.weather().pending();
        var later = now.plusMinutes(10);
        assertThat(engine.evaluate(1, cfg, later, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(later, 0, null, "clear", pending, "c2")).due()).isFalse();
        var expired = engine.evaluate(1, cfg, now.plusHours(2), "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(now.plusHours(2), 0, null, "clear", pending, "c1"));
        assertThat(expired.due()).isFalse(); assertThat(expired.executionKey()).isNotEqualTo(first.executionKey());
        var limited = engine.evaluate(1, cfg, later, "UTC", true, null, null, List.of(), null, 1190, false, null,
                weather(later, 0, null, "clear", pending, "c1"));
        assertThat(limited.due()).isFalse(); assertThat(limited.status()).isEqualTo("limited");
    }

    private ru.growerhub.backend.automation.contract.WeatherForecastData.Input weather(LocalDateTime now, double mm,
            Double probability, String symbol, ru.growerhub.backend.automation.contract.WeatherForecastData.Pending pending, String fingerprint) {
        var start = now.withMinute(0).withSecond(0).withNano(0);
        var periods = new ArrayList<ru.growerhub.backend.automation.contract.WeatherForecastData.Period>();
        for (int i = 0; i < 48; i++) periods.add(new ru.growerhub.backend.automation.contract.WeatherForecastData.Period(
                start.plusHours(i), start.plusHours(i + 1), mm, probability, symbol));
        return new ru.growerhub.backend.automation.contract.WeatherForecastData.Input(
                new ru.growerhub.backend.automation.contract.WeatherForecastData.Forecast("MET Norway", now, now, periods, null), pending, fingerprint);
    }

    @Test
    void releaseWaitsForPermittedHoursAndUsesActualExecutionDayLimit() {
        var cfg = config("schedule"); cfg.putAll(Map.of("weather_enabled", true, "rain_exposure", "outdoors", "rain_max_delay_hours", 48));
        var now = LocalDateTime.parse("2026-10-03T07:00:30");
        var first = engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(now, 1, null, "rain", null, "c"));
        var pending = first.weather().pending();
        var evening = now.withHour(20);
        var clear = engine.evaluate(1, cfg, evening, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(evening, 0, null, "clear", pending, "c"));
        assertThat(clear.due()).isFalse(); assertThat(clear.plannedAt()).isEqualTo(LocalDateTime.parse("2026-10-04T07:00:00"));
        var tomorrow = now.plusDays(1);
        var limited = engine.evaluate(1, cfg, tomorrow, "UTC", true, null, null, List.of(), null, 1190, false, null,
                weather(tomorrow, 0, null, "clear", pending, "c"));
        assertThat(limited.due()).isFalse(); assertThat(limited.status()).isEqualTo("limited");
        var ready = engine.evaluate(1, cfg, tomorrow, "UTC", true, null, null, List.of(), null, 0, false, null,
                weather(tomorrow, 0, null, "clear", pending, "c"));
        assertThat(ready.due()).isTrue(); assertThat(ready.executionKey()).isEqualTo(first.executionKey());
        cfg.put("schedule_time", "20:00");
        assertThatThrownBy(() -> engine.validate(cfg)).isInstanceOf(DomainException.class).hasMessageContaining("окно");
    }

    @Test
    void soilRainHoldDoesNotResetItsDeadlineAtMidnight() {
        var now = LocalDateTime.parse("2026-10-02T23:30:00");
        var last = mock(PumpSessionData.View.class);
        when(last.id()).thenReturn(20L); when(last.phase()).thenReturn("completed"); when(last.activeDurationS()).thenReturn(30);
        when(last.startedAt()).thenReturn(now.minusHours(16).minusMinutes(1)); when(last.finishedAt()).thenReturn(now.minusHours(16));
        var history = new ArrayList<>(responseHistory(now));
        var cfg = config("drying");
        cfg.putAll(Map.of("wet_anchor", 70, "dry_anchor", 35, "calibration_binding_key", "sensor:1",
                "window_start", "00:00", "window_end", "00:00", "weather_enabled", true,
                "rain_exposure", "outdoors", "rain_max_delay_hours", 1));
        var first = engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", history, last, 0, false, null,
                weather(now, 1, null, "rain", null, "c"));
        var pending = first.weather().pending();
        var midnight = now.plusMinutes(45);
        history.add(new WateringPlanData.Point(midnight, 30.6));
        var held = engine.evaluate(1, cfg, midnight, "UTC", true, null, "sensor:1", history, last, 0, false, null,
                weather(midnight, 1, null, "rain", pending, "c"));
        assertThat(held.executionKey()).isEqualTo(first.executionKey());
        assertThat(held.weather().pending().deadline()).isEqualTo(pending.deadline());
        var later = now.plusHours(2);
        history.add(new WateringPlanData.Point(later, 30.0));
        var expired = engine.evaluate(1, cfg, later, "UTC", true, null, "sensor:1", history, last, 0, false, null,
                weather(later, 0, null, "clear", held.weather().pending(), "c"));
        assertThat(expired.due()).isFalse(); assertThat(expired.weather().status()).isEqualTo("expired");
        assertThat(expired.weather().pending().deadline()).isEqualTo(pending.deadline());
    }
}
