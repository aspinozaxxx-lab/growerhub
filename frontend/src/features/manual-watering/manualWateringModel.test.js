import { describe, expect, it } from 'vitest';
import {
  completionReasonLabel,
  formatDateTime,
  formatDurationSeconds,
  formatVolumeLiters,
  normalizeManualWateringOverview,
  normalizeSessionPage,
  overviewHasActiveSession,
  pumpCanStart,
  startBlockReasonLabel,
  wateringDefaultsReady,
} from './manualWateringModel';

describe('manual watering model', () => {
  it('ispolzuet tolko servernye defaults', () => {
    const overview = normalizeManualWateringOverview({
      defaults: {
        timed_duration_s: 420,
        until_leak_max_active_duration_s: 1800,
        pulse_run_s: 180,
        pulse_pause_s: 300,
      },
      pumps: [{ id: 1 }],
    });

    expect(overview.defaults).toEqual({
      timed_duration_s: 420,
      until_leak_max_active_duration_s: 1800,
      pulse_run_s: 180,
      pulse_pause_s: 300,
    });
    expect(wateringDefaultsReady(overview.defaults)).toBe(true);
    expect(wateringDefaultsReady(normalizeManualWateringOverview({}).defaults)).toBe(false);
  });

  it('ne dubliruet backend eligibility na fronte', () => {
    expect(pumpCanStart({ capabilities: { can_start: true } })).toBe(true);
    expect(pumpCanStart({ is_online: true, boxes: [{ plants: [{}] }] })).toBe(false);
    expect(startBlockReasonLabel('leak_triggered')).toBe('Один из датчиков уже показывает протечку');
  });

  it('opredelyaet aktivnuyu sessiyu iz overview', () => {
    expect(overviewHasActiveSession({ pumps: [{ current_session: { id: 5 } }] })).toBe(true);
    expect(overviewHasActiveSession({ pumps: [{ current_session: null }] })).toBe(false);
  });

  it('normalizuet paginaciyu sessiy', () => {
    expect(normalizeSessionPage({ items: [{ id: 4 }], next_before_id: 4 })).toEqual({
      items: [{ id: 4 }],
      nextBeforeId: 4,
    });
  });

  it('formatiruet dlitelnost obem i prichinu', () => {
    expect(formatDurationSeconds(3665)).toBe('1 ч 1 мин 5 сек');
    expect(formatVolumeLiters(0.1234)).toBe('0,123 л');
    expect(formatVolumeLiters(null)).toBe('Не рассчитан');
    expect(completionReasonLabel('leak')).toBe('Остановлен по протечке');
    expect(formatDateTime('2026-07-11T12:00:00')).toBe('11.07.2026, 15:00');
  });
});
