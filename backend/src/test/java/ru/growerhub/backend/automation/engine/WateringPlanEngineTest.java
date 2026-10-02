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
            90, 168, 8, 4, 120, 1, 0.5, 0.001, 45, 6, 0.05, 6, 1200, "07:00", "10:00"));

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
        var due = engine.evaluate(10, cfg, now, "Europe/Istanbul", true, null, null, List.of(), null, 0, false, null);
        assertThat(due.due()).isTrue();
        assertThat(due.soil()).isNull();
        assertThat(due.plannedAt()).isEqualTo(LocalDateTime.parse("2026-10-02T04:00:00"));
        var consumed = engine.evaluate(10, cfg, now, "Europe/Istanbul", true, null, null, List.of(), null, 0, false, due.executionKey());
        assertThat(consumed.due()).isFalse();
        assertThat(consumed.plannedAt()).isEqualTo(LocalDateTime.parse("2026-10-09T04:00:00"));
        var late = engine.evaluate(10, cfg, now.plusMinutes(5), "Europe/Istanbul", true, null, null, List.of(), null, 0, false, null);
        assertThat(late.due()).isFalse();
        assertThat(late.plannedAt()).isEqualTo(consumed.plannedAt());
    }

    @Test
    void observationDisabledEquipmentAndLimitsNeverProduceCommands() {
        var cfg = config("schedule");
        var now = LocalDateTime.parse("2026-10-02T07:00:00");
        cfg.put("observe_only", true);
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, false, null).due()).isFalse();
        cfg.put("observe_only", false);
        assertThat(engine.evaluate(1, cfg, now, "UTC", false, null, null, List.of(), null, 0, false, null).due()).isFalse();
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, "Нет связи", null, List.of(), null, 0, false, null).status()).isEqualTo("unready");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 1190, false, null).status()).isEqualTo("limited");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, null, List.of(), null, 0, true, null).due()).isFalse();
    }

    @Test
    void daylightSavingGapSkipsSlotAndOverlapUsesOneLocalExecutionKey() {
        var cfg = config("schedule"); cfg.put("schedule_time", "02:30");
        var gap = engine.evaluate(1, cfg, LocalDateTime.parse("2026-03-29T00:00:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, null);
        assertThat(gap.plannedAt()).isEqualTo(LocalDateTime.parse("2026-03-30T00:30:00"));
        var first = engine.evaluate(1, cfg, LocalDateTime.parse("2026-10-25T00:30:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, null);
        assertThat(first.due()).isTrue();
        var second = engine.evaluate(1, cfg, LocalDateTime.parse("2026-10-25T01:30:00"), "Europe/Berlin", true, null, null, List.of(), null, 0, false, first.executionKey());
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
        var plan = engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", history, last, 0, false, null);
        assertThat(plan.soil().responseVerified()).isTrue();
        assertThat(plan.soil().dryingFraction()).isGreaterThanOrEqualTo(1);
        assertThat(plan.due()).isTrue();
        cfg.put("wet_anchor", 30); cfg.put("dry_anchor", 65);
        var reverse = history.stream().map(p -> new WateringPlanData.Point(p.ts(), 100 - p.value())).toList();
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", reverse, last, 0, false, null).due()).isTrue();
        var moved = engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:2", reverse, last, 0, false, null);
        assertThat(moved.due()).isFalse();
        assertThat(moved.soil().issue()).contains("ориентиры");
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", reverse, null, 0, false, null).due()).isFalse();
    }

    @Test
    void staleJumpAndFlatHistoryAreNotDrynessTriggers() {
        var now = LocalDateTime.parse("2026-10-02T12:00:00");
        var cfg = config("drying"); cfg.putAll(Map.of("wet_anchor", 70, "dry_anchor", 35, "calibration_binding_key", "sensor:1"));
        var points = new ArrayList<WateringPlanData.Point>();
        for (int i = 0; i < 12; i++) points.add(new WateringPlanData.Point(now.minusHours(11 - i), 30.0));
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", points, null, 0, false, null).due()).isFalse();
        points.set(11, new WateringPlanData.Point(now, 90.0));
        assertThat(engine.evaluate(1, cfg, now, "UTC", true, null, "sensor:1", points, null, 0, false, null).soil().issue()).contains("скачок");
        assertThat(engine.evaluate(1, cfg, now.plusHours(3), "UTC", true, null, "sensor:1", points, null, 0, false, null).soil().issue()).contains("устарели");
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
}
