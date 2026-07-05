import React from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchAdminPumpHistory, fetchAdminZigbeeHistory } from '../../api/admin';
import { fetchPlantHistory } from '../../api/plants';
import { fetchSensorHistory } from '../../api/sensors';
import { useSensorStats } from './useSensorStats';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ token: 'token-1' }),
}));

vi.mock('../../api/admin', () => ({
  fetchAdminPumpHistory: vi.fn(),
  fetchAdminZigbeeHistory: vi.fn(),
}));

vi.mock('../../api/plants', () => ({
  fetchPlantHistory: vi.fn(),
}));

vi.mock('../../api/sensors', () => ({
  fetchSensorHistory: vi.fn(),
}));

function Probe(props) {
  const { chartData, dailyOnDurations, isLoading, isDailyLoading, error } = useSensorStats(props);
  return (
    <div>
      <div data-testid="loading">{String(isLoading)}</div>
      <div data-testid="daily-loading">{String(isDailyLoading)}</div>
      <div data-testid="error">{error || ''}</div>
      <pre data-testid="data">{JSON.stringify(chartData)}</pre>
      <pre data-testid="daily">{JSON.stringify(dailyOnDurations)}</pre>
    </div>
  );
}

describe('useSensorStats', () => {
  beforeEach(() => {
    fetchAdminPumpHistory.mockReset();
    fetchAdminZigbeeHistory.mockReset();
    fetchPlantHistory.mockReset();
    fetchSensorHistory.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it('zagruzhaet istoriyu native sensora', async () => {
    fetchSensorHistory.mockResolvedValueOnce([{ ts: '2026-01-01T00:00:00', value: 23.5 }]);

    render(<Probe mode="sensor" sensorId={7} metric="air_temperature" />);

    await waitFor(() => expect(fetchSensorHistory).toHaveBeenCalledWith(7, 24, 'token-1'));
    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('23.5'));
  });

  it('zagruzhaet istoriyu metriki rasteniya', async () => {
    fetchPlantHistory.mockResolvedValueOnce([
      { ts: '2026-01-01T00:00:00', metric_type: 'SOIL_MOISTURE', value: 40 },
    ]);

    render(<Probe mode="plant" plantId={3} metric="soil_moisture" />);

    await waitFor(() => expect(fetchPlantHistory).toHaveBeenCalledWith(3, 24, 'SOIL_MOISTURE', 'token-1'));
    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('40'));
  });

  it('zagruzhaet istoriyu Zigbee svojstva', async () => {
    fetchAdminZigbeeHistory.mockResolvedValueOnce([
      { ts: '2026-01-01T00:00:00', value: 1, raw_value: 'ON' },
    ]);

    render(
      <Probe
        mode="zigbee"
        zigbeeIeeeAddress="0xabc"
        zigbeeProperty="state"
        metric="device_state"
      />,
    );

    await waitFor(() => expect(fetchAdminZigbeeHistory).toHaveBeenCalledWith('0xabc', 'state', 24, 'token-1'));
    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('ON'));
  });

  it('gotovit chislovuyu vremennuyu os dlya grafikovyh intervalov', async () => {
    fetchAdminZigbeeHistory.mockResolvedValueOnce([
      { ts: '2026-01-01T00:26:00Z', value: 1, raw_value: 'ON' },
      { ts: '2026-01-01T00:00:00Z', value: 1, raw_value: 'ON' },
      { ts: '2026-01-01T00:13:00Z', value: 0, raw_value: 'OFF' },
    ]);

    render(
      <Probe
        mode="zigbee"
        zigbeeIeeeAddress="0xabc"
        zigbeeProperty="state"
        metric="device_state"
      />,
    );

    await waitFor(() => expect(fetchAdminZigbeeHistory).toHaveBeenCalledWith('0xabc', 'state', 24, 'token-1'));
    await waitFor(() => {
      const data = JSON.parse(screen.getByTestId('data').textContent);
      expect(data.map((point) => point.value)).toEqual([1, 0, 1]);
      expect(data[1].timeMs - data[0].timeMs).toBe(13 * 60 * 1000);
      expect(data[2].timeMs - data[1].timeMs).toBe(13 * 60 * 1000);
    });
  });

  it('schitaet vremya vklyuchennogo sostoyaniya po dnyam za poslednyuyu nedelyu', async () => {
    const nowSpy = vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-01-08T12:00:00Z').getTime());
    fetchAdminZigbeeHistory
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        { ts: '2026-01-06T20:00:00Z', value: 1, raw_value: 'ON' },
        { ts: '2026-01-07T01:30:00Z', value: 0, raw_value: 'OFF' },
        { ts: '2026-01-08T06:00:00Z', value: 1, raw_value: 'ON' },
      ]);

    try {
      render(
        <Probe
          mode="zigbee"
          zigbeeIeeeAddress="0xabc"
          zigbeeProperty="state"
          metric="device_state"
          chartKind="binary"
        />,
      );

      await waitFor(() => expect(fetchAdminZigbeeHistory).toHaveBeenCalledWith('0xabc', 'state', 168, 'token-1'));
      await waitFor(() => {
        const daily = JSON.parse(screen.getByTestId('daily').textContent);
        expect(daily).toHaveLength(3);
        expect(daily.map((day) => day.dateKey)).toEqual(['2026-01-08', '2026-01-07', '2026-01-06']);
        expect(daily.map((day) => day.durationMs)).toEqual([
          6 * 60 * 60 * 1000,
          4.5 * 60 * 60 * 1000,
          60 * 60 * 1000,
        ]);
      });
    } finally {
      nowSpy.mockRestore();
    }
  });

  it('zagruzhaet istoriyu native nasosa', async () => {
    fetchAdminPumpHistory.mockResolvedValueOnce([
      { ts: '2026-01-01T00:00:00', is_running: true, raw_status: 'running' },
    ]);

    render(<Probe mode="pump" pumpId={9} metric="pump" />);

    await waitFor(() => expect(fetchAdminPumpHistory).toHaveBeenCalledWith(9, 24, 'token-1'));
    await waitFor(() => expect(screen.getByTestId('data')).toHaveTextContent('running'));
    expect(screen.getByTestId('data')).toHaveTextContent('"value":1');
  });
});
