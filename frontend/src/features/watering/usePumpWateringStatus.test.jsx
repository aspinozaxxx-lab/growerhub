import React from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import usePumpWateringStatus from './usePumpWateringStatus';
import { WateringSidebarProvider, useWateringSidebar } from './WateringSidebarContext';

const fetchPumpWateringStatus = vi.fn();
const stopPumpWatering = vi.fn();

vi.mock('../../api/pumps', () => ({
  fetchPumpWateringStatus: (...args) => fetchPumpWateringStatus(...args),
  stopPumpWatering: (...args) => stopPumpWatering(...args),
}));

function Probe({ pumpId = 7 }) {
  const { setWateringStatus, refreshVersion } = useWateringSidebar();
  const { status, remainingSeconds, stop } = usePumpWateringStatus(pumpId, { enabled: false });

  return (
    <div>
      <div data-testid="status">{status ?? 'null'}</div>
      <div data-testid="remaining">{remainingSeconds ?? 'null'}</div>
      <div data-testid="refresh">{refreshVersion}</div>
      <button
        type="button"
        onClick={() => setWateringStatus(pumpId, {
          status: 'running',
          remaining_s: 1,
          duration_s: 1,
        })}
      >
        start-local
      </button>
      <button
        type="button"
        onClick={() => setWateringStatus(pumpId, {
          status: 'running',
          remaining_s: 0,
          duration_s: 1,
        })}
      >
        start-zero-local
      </button>
      <button type="button" onClick={() => stop()}>
        stop
      </button>
    </div>
  );
}

function renderProbe() {
  return render(
    <WateringSidebarProvider>
      <Probe />
    </WateringSidebarProvider>,
  );
}

async function flushAsync() {
  await act(async () => {
    await Promise.resolve();
  });
}

async function startLocalWatering() {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'start-local' }));
  });
  await flushAsync();
  await flushAsync();
}

async function startZeroLocalWatering() {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'start-zero-local' }));
  });
  await flushAsync();
  await flushAsync();
}

describe('usePumpWateringStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    fetchPumpWateringStatus.mockReset();
    stopPumpWatering.mockReset();
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

  it('delaet server refetch na nule i skryvaet plashku tolko posle podtverzhdenija servera', async () => {
    fetchPumpWateringStatus.mockResolvedValueOnce({ status: 'idle', remaining_s: 0, duration_s: 1 });

    renderProbe();
    await startZeroLocalWatering();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('status')).toHaveTextContent('idle');
    expect(screen.getByTestId('refresh')).toHaveTextContent('1');
  });

  it('pri nule peresinhroniziruet timer esli server govorit chto poliv eshche idet', async () => {
    fetchPumpWateringStatus.mockResolvedValueOnce({ status: 'running', remaining_s: 5, duration_s: 5 });

    renderProbe();
    await startZeroLocalWatering();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('status')).toHaveTextContent('running');
    expect(Number(screen.getByTestId('remaining').textContent)).toBeGreaterThanOrEqual(4);
    expect(screen.getByTestId('refresh')).toHaveTextContent('0');
  });

  it('sinhroniziruetsja s serverom na focus i visibilitychange', async () => {
    fetchPumpWateringStatus
      .mockResolvedValueOnce({ status: 'running', remaining_s: 30, duration_s: 30 })
      .mockResolvedValueOnce({ status: 'running', remaining_s: 25, duration_s: 30 })
      .mockResolvedValueOnce({ status: 'running', remaining_s: 20, duration_s: 30 });

    renderProbe();
    await startLocalWatering();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(1);

    await act(async () => {
      fireEvent.focus(window);
    });
    await flushAsync();
    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(2);

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'hidden',
    });
    await act(async () => {
      fireEvent(document, new Event('visibilitychange'));
    });
    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(2);

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
    await act(async () => {
      fireEvent(document, new Event('visibilitychange'));
    });
    await flushAsync();
    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(3);
  });

  it('delaet redkij refetch poka poliv aktiven i vkladka vidima', async () => {
    fetchPumpWateringStatus
      .mockResolvedValueOnce({ status: 'running', remaining_s: 30, duration_s: 30 })
      .mockResolvedValueOnce({ status: 'running', remaining_s: 20, duration_s: 30 });

    renderProbe();
    await startLocalWatering();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    await flushAsync();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(2);
  });

  it('posle ruchnogo stop ubiraet lokalnyj status i obnavljaet globalnyj refresh', async () => {
    fetchPumpWateringStatus
      .mockResolvedValueOnce({ status: 'running', remaining_s: 30, duration_s: 30 })
      .mockResolvedValueOnce({ status: 'idle', remaining_s: 0, duration_s: 30 });
    stopPumpWatering.mockResolvedValue({});

    renderProbe();
    await startLocalWatering();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'stop' }));
    });
    await flushAsync();
    await flushAsync();

    expect(stopPumpWatering).toHaveBeenCalledTimes(1);
    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('status')).toHaveTextContent('idle');
    expect(screen.getByTestId('refresh')).toHaveTextContent('1');
  });

  it('ne skryvaet plashku bez servernogo podtverzhdenija esli proverka na nule padayet', async () => {
    fetchPumpWateringStatus.mockRejectedValueOnce(new Error('network'));

    renderProbe();
    await startZeroLocalWatering();

    expect(fetchPumpWateringStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('status')).toHaveTextContent('running');
    expect(screen.getByTestId('refresh')).toHaveTextContent('0');
  });
});
