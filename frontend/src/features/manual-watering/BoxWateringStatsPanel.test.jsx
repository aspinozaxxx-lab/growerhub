import React from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BoxWateringStatsPanel from './BoxWateringStatsPanel';

const fetchStatistics = vi.fn();
const stopWatering = vi.fn();

vi.mock('../../api/admin', () => ({
  fetchAdminBoxWateringStatistics: (...args) => fetchStatistics(...args),
  stopAdminManualWatering: (...args) => stopWatering(...args),
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ token: 'token' }),
}));

describe('BoxWateringStatsPanel', () => {
  beforeEach(() => {
    fetchStatistics.mockReset();
    stopWatering.mockReset();
    fetchStatistics.mockResolvedValue({
      box_id: 2,
      range: 'day',
      session_count: 2,
      active_duration_s: 420,
      known_volume_l: 0.35,
      partial_volume: true,
      mode_counts: { timed: 1, until_leak: 1 },
      reason_counts: { duration: 1, leak: 1 },
      sessions: [],
      active_session: null,
      next_before_id: null,
    });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('zagruzhaet box scoped statistiku i menyaet kalendarnyj diapazon', async () => {
    render(<BoxWateringStatsPanel target={{ boxId: 2, pumpId: 7, title: 'Бокс 2' }} onClose={vi.fn()} />);

    await waitFor(() => expect(fetchStatistics).toHaveBeenCalledWith(2, {
      range: 'day',
      limit: 10,
      beforeId: null,
    }, 'token'));
    expect(screen.getByText('7 мин')).toBeInTheDocument();
    expect(screen.getByText('0,35 л')).toBeInTheDocument();
    expect(screen.getByText('Есть растения без указанной скорости')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Неделя' }));
    await waitFor(() => expect(fetchStatistics).toHaveBeenLastCalledWith(2, {
      range: 'week',
      limit: 10,
      beforeId: null,
    }, 'token'));
  });

  it('preduprezhdaet ob ostanovke vseh boksov i ostanavlivaet aktivnyj nasos', async () => {
    fetchStatistics.mockResolvedValue({
      session_count: 0,
      active_duration_s: 0,
      known_volume_l: null,
      partial_volume: false,
      mode_counts: {},
      reason_counts: {},
      sessions: [],
      active_session: {
        id: 5,
        pump_id: 7,
        phase: 'running',
        mode: 'timed',
        active_duration_s: 60,
      },
    });
    stopWatering.mockResolvedValue({});
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<BoxWateringStatsPanel target={{ boxId: 2, pumpId: 7, title: 'Бокс 2' }} onClose={vi.fn()} />);

    const stopButton = await screen.findByRole('button', { name: 'Остановить' });
    fireEvent.click(stopButton);

    await waitFor(() => expect(stopWatering).toHaveBeenCalledWith(7, 'token'));
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('во всех привязанных'));
  });

  it('ignoriruet zapozdalyj otvet predydushchego diapazona', async () => {
    let resolveDay;
    let resolveWeek;
    fetchStatistics.mockImplementation((_boxId, options) => new Promise((resolve) => {
      if (options.range === 'day') resolveDay = resolve;
      if (options.range === 'week') resolveWeek = resolve;
    }));
    render(<BoxWateringStatsPanel target={{ boxId: 2, pumpId: 7, title: 'Бокс 2' }} onClose={vi.fn()} />);
    await waitFor(() => expect(resolveDay).toBeTypeOf('function'));

    fireEvent.click(screen.getByRole('button', { name: 'Неделя' }));
    await waitFor(() => expect(resolveWeek).toBeTypeOf('function'));
    await act(async () => resolveWeek({
      range: 'week',
      session_count: 2,
      active_duration_s: 120,
      known_volume_l: null,
      partial_volume: false,
      mode_counts: {},
      reason_counts: {},
      sessions: [],
      active_session: null,
    }));
    expect(within(screen.getByLabelText('Итоги периода')).getByText('2')).toBeInTheDocument();

    await act(async () => resolveDay({
      range: 'day',
      session_count: 99,
      active_duration_s: 999,
      known_volume_l: null,
      partial_volume: false,
      mode_counts: {},
      reason_counts: {},
      sessions: [],
      active_session: null,
    }));
    expect(screen.queryByText('99')).not.toBeInTheDocument();
    expect(within(screen.getByLabelText('Итоги периода')).getByText('2')).toBeInTheDocument();
  });

  it('sohranyaet zagruzhennye stranicy pri aktivnom refresh i ubiraet dublikaty', async () => {
    const session = (id, completionReason) => ({
      id,
      source: 'automation',
      mode: 'timed',
      phase: 'completed',
      active_duration_s: 60,
      started_at: `2026-07-11T0${id}:00:00`,
      finished_at: `2026-07-11T0${id}:01:00`,
      completion_reason: completionReason,
    });
    const aggregate = {
      session_count: 4,
      active_duration_s: 240,
      known_volume_l: null,
      partial_volume: false,
      mode_counts: { timed: 4 },
      reason_counts: {},
      active_session: { id: 10, pump_id: 7, phase: 'running', mode: 'timed', active_duration_s: 30 },
    };
    fetchStatistics
      .mockResolvedValueOnce({ ...aggregate, sessions: [session(3, 'duration'), session(2, 'leak')], next_before_id: 2 })
      .mockResolvedValueOnce({ ...aggregate, sessions: [session(1, 'manual')], next_before_id: null })
      .mockResolvedValueOnce({ ...aggregate, sessions: [session(4, 'recovery'), session(3, 'duration')], next_before_id: 3 });
    render(<BoxWateringStatsPanel target={{ boxId: 2, pumpId: 7, title: 'Бокс 2' }} onClose={vi.fn()} />);

    const loadMore = await screen.findByRole('button', { name: 'Показать ещё' });
    fireEvent.click(loadMore);
    await screen.findByText('Остановлен вручную');

    await act(async () => {
      fireEvent.focus(window);
      await Promise.resolve();
    });
    await screen.findByText('Завершён при восстановлении после перезапуска');

    expect(screen.getByText('Остановлен вручную')).toBeInTheDocument();
    expect(screen.getAllByText('Завершён по времени')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Показать ещё' })).not.toBeInTheDocument();
  });
});
