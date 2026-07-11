import React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import useAdminManualWatering from './useAdminManualWatering';

const fetchOverview = vi.fn();
const fetchSessions = vi.fn();
const startWateringApi = vi.fn();
const stopWateringApi = vi.fn();

vi.mock('../../api/admin', () => ({
  fetchAdminManualWateringOverview: (...args) => fetchOverview(...args),
  fetchAdminManualWateringSessions: (...args) => fetchSessions(...args),
  startAdminManualWatering: (...args) => startWateringApi(...args),
  stopAdminManualWatering: (...args) => stopWateringApi(...args),
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ token: 'token' }),
}));

function Probe() {
  const {
    overview,
    histories,
    actionError,
    loadSessions,
    startWatering,
    stopWatering,
  } = useAdminManualWatering();
  return (
    <div>
      <div data-testid="count">{overview?.pumps?.length ?? 'loading'}</div>
      <div data-testid="overview-session">{overview?.pumps?.[0]?.current_session?.id ?? 'none'}</div>
      <div data-testid="history-phase">{histories[1]?.items?.[0]?.phase || 'none'}</div>
      <div data-testid="history-ids">{histories[1]?.items?.map((item) => item.id).join(',') || 'none'}</div>
      <div data-testid="action-error">{actionError || 'none'}</div>
      <button type="button" onClick={() => loadSessions(1)}>load-history</button>
      <button type="button" onClick={() => loadSessions(1, { append: true })}>append-history</button>
      <button type="button" onClick={() => startWatering(1, { mode: 'timed', duration_s: 300 })}>start-action</button>
      <button type="button" onClick={() => stopWatering(1)}>stop-action</button>
    </div>
  );
}

describe('useAdminManualWatering', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetchOverview.mockReset();
    fetchSessions.mockReset();
    startWateringApi.mockReset();
    stopWateringApi.mockReset();
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('pollit aktivnuyu sessiyu raz v tri sekundy', async () => {
    fetchOverview.mockResolvedValue({
      pumps: [{ id: 1, current_session: { id: 10, phase: 'running' } }],
    });

    await act(async () => render(<Probe />));
    expect(screen.getByTestId('count')).toHaveTextContent('1');
    expect(fetchOverview).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(fetchOverview).toHaveBeenCalledTimes(2);
  });

  it('pollit idle sostoyanie raz v pyatnadcat sekund i ne pollit skrytuyu vkladku', async () => {
    fetchOverview.mockResolvedValue({ pumps: [{ id: 1, current_session: null }] });
    await act(async () => render(<Probe />));

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15000);
    });
    expect(fetchOverview).toHaveBeenCalledTimes(1);

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });
    expect(fetchOverview).toHaveBeenCalledTimes(2);
  });

  it('obnovlyaet overview pri vozvrate fokusa', async () => {
    fetchOverview.mockResolvedValue({ pumps: [] });
    await act(async () => render(<Probe />));
    expect(fetchOverview).toHaveBeenCalledTimes(1);

    await act(async () => {
      fireEvent.focus(window);
      await Promise.resolve();
    });
    expect(fetchOverview).toHaveBeenCalledTimes(2);
  });

  it('ne pozvolyaet ustarevshemu overview zatirat bolee svezhij otvet', async () => {
    let resolveFirst;
    let resolveSecond;
    fetchOverview
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve; }));

    render(<Probe />);
    await act(async () => {
      fireEvent.focus(window);
      await Promise.resolve();
    });
    await act(async () => {
      resolveSecond({ pumps: [{ id: 1, current_session: { id: 20, phase: 'running' } }] });
      await Promise.resolve();
    });
    expect(screen.getByTestId('overview-session')).toHaveTextContent('20');

    await act(async () => {
      resolveFirst({ pumps: [{ id: 1, current_session: null }] });
      await Promise.resolve();
    });
    expect(screen.getByTestId('overview-session')).toHaveTextContent('20');
  });

  it('obnovlyaet uzhe otkrytyj zhurnal posle avtomaticheskogo zaversheniya', async () => {
    fetchOverview
      .mockResolvedValueOnce({ pumps: [{ id: 1, current_session: { id: 10, phase: 'running' } }] })
      .mockResolvedValueOnce({ pumps: [{ id: 1, current_session: null }] });
    fetchSessions
      .mockResolvedValueOnce({ items: [{ id: 10, phase: 'running' }], next_before_id: null })
      .mockResolvedValueOnce({ items: [{ id: 10, phase: 'completed' }], next_before_id: null });

    await act(async () => render(<Probe />));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'load-history' }));
      await Promise.resolve();
    });
    expect(screen.getByTestId('history-phase')).toHaveTextContent('running');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
      await Promise.resolve();
    });

    expect(fetchSessions).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('history-phase')).toHaveTextContent('completed');
  });

  it('hranitr oshibku start otdelno ot overview dlya pokaza v modal', async () => {
    fetchOverview.mockResolvedValue({ pumps: [] });
    startWateringApi.mockRejectedValue(new Error('Запуск запрещён сервером'));
    await act(async () => render(<Probe />));

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'start-action' }));
      await Promise.resolve();
    });

    expect(screen.getByTestId('action-error')).toHaveTextContent('Запуск запрещён сервером');
  });

  it('sohranyaet glubinu zhurnala pri refresh posle zaversheniya', async () => {
    fetchOverview
      .mockResolvedValueOnce({ pumps: [{ id: 1, current_session: { id: 10, phase: 'running' } }] })
      .mockResolvedValueOnce({ pumps: [{ id: 1, current_session: null }] });
    fetchSessions
      .mockResolvedValueOnce({ items: [{ id: 3 }, { id: 2 }], next_before_id: 2 })
      .mockResolvedValueOnce({ items: [{ id: 1 }], next_before_id: null })
      .mockResolvedValueOnce({ items: [{ id: 4 }, { id: 3 }], next_before_id: 3 });
    await act(async () => render(<Probe />));

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'load-history' }));
      await Promise.resolve();
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'append-history' }));
      await Promise.resolve();
    });
    expect(screen.getByTestId('history-ids')).toHaveTextContent('3,2,1');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
      await Promise.resolve();
    });
    expect(screen.getByTestId('history-ids')).toHaveTextContent('4,3,2,1');
  });
});
