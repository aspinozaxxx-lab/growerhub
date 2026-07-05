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
  const { chartData, isLoading, error } = useSensorStats(props);
  return (
    <div>
      <div data-testid="loading">{String(isLoading)}</div>
      <div data-testid="error">{error || ''}</div>
      <pre data-testid="data">{JSON.stringify(chartData)}</pre>
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
