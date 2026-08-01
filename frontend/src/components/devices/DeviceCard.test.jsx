import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DeviceCard from './DeviceCard';

vi.mock('../../features/sensors/SensorStatsContext', () => ({
  useSensorStatsContext: () => ({ openSensorStats: vi.fn() }),
}));

vi.mock('../../features/watering/usePumpWateringStatus', () => ({
  default: () => ({ remainingSeconds: 0, isRunning: false, stop: vi.fn() }),
}));

const device = {
  id: 1,
  device_id: 'GROVIKA_040AB1',
  name: 'Grovika Mini',
  is_online: true,
  firmware_version: 'grovika-1',
  sensors: [],
  pumps: [],
};

describe('DeviceCard firmware', () => {
  afterEach(() => cleanup());

  it('pokazyvaet novuyu versiyu i zapuskaet obnovlenie', () => {
    const onFirmwareUpdate = vi.fn();
    render(
      <DeviceCard
        device={device}
        firmwareStatus={{
          current_version: 'grovika-1',
          latest_version: 'grovika-2',
          update_available: true,
          status: 'IDLE',
        }}
        onFirmwareUpdate={onFirmwareUpdate}
      />,
    );

    expect(screen.getByText('Доступна новая версия: grovika-2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Обновить прошивку' }));
    expect(onFirmwareUpdate).toHaveBeenCalledTimes(1);
  });

  it('pokazyvaet uspeshnuyu ustanovku poslednei versii', () => {
    render(
      <DeviceCard
        device={{ ...device, firmware_version: 'grovika-2' }}
        firmwareStatus={{
          current_version: 'grovika-2',
          latest_version: 'grovika-2',
          update_available: false,
          status: 'SUCCESS',
        }}
      />,
    );

    expect(screen.getByText('Установлена последняя версия')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Обновить прошивку' })).not.toBeInTheDocument();
  });

  it('pokazyvaet kod oshibki ponyatnym tekstom i razreshaet povtor', () => {
    render(
      <DeviceCard
        device={device}
        firmwareStatus={{
          current_version: 'grovika-1',
          latest_version: 'grovika-2',
          update_available: true,
          status: 'ERROR',
          error: 'firmware_sha256_mismatch',
        }}
        onFirmwareUpdate={vi.fn()}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Контрольная сумма прошивки не совпала');
    expect(screen.getByRole('button', { name: 'Повторить обновление' })).toBeInTheDocument();
  });
});
