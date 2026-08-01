import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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

function renderDeviceCard(props = {}) {
  return render(
    <MemoryRouter>
      <DeviceCard device={device} {...props} />
    </MemoryRouter>,
  );
}

describe('DeviceCard firmware', () => {
  afterEach(() => cleanup());

  it('pokazyvaet novuyu versiyu i zapuskaet obnovlenie', () => {
    const onFirmwareUpdate = vi.fn();
    renderDeviceCard({
      firmwareStatus: {
        current_version: 'grovika-1',
        latest_version: 'grovika-2',
        update_available: true,
        status: 'IDLE',
      },
      onFirmwareUpdate,
    });

    expect(screen.getByText('Доступна новая версия: grovika-2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Обновить прошивку' }));
    expect(onFirmwareUpdate).toHaveBeenCalledTimes(1);
  });

  it('pokazyvaet uspeshnuyu ustanovku poslednei versii', () => {
    renderDeviceCard({
      device: { ...device, firmware_version: 'grovika-2' },
      firmwareStatus: {
        current_version: 'grovika-2',
        latest_version: 'grovika-2',
        update_available: false,
        status: 'SUCCESS',
      },
    });

    expect(screen.getByText('Установлена последняя версия')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Обновить прошивку' })).not.toBeInTheDocument();
  });

  it('pokazyvaet kod oshibki ponyatnym tekstom i razreshaet povtor', () => {
    renderDeviceCard({
      firmwareStatus: {
        current_version: 'grovika-1',
        latest_version: 'grovika-2',
        update_available: true,
        status: 'ERROR',
        error: 'firmware_sha256_mismatch',
      },
      onFirmwareUpdate: vi.fn(),
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Контрольная сумма прошивки не совпала');
    expect(screen.getByRole('button', { name: 'Повторить обновление' })).toBeInTheDocument();
  });

  it('pokazyvaet teplicu i roli vmesto compatibility privyazok k rasteniyam', () => {
    renderDeviceCard({
      device: {
        ...device,
        sensors: [{
          id: 2,
          type: 'SOIL_MOISTURE',
          label: 'Почва',
          bound_plants: [{ id: 10, name: 'Розмарин' }],
        }],
        pumps: [{
          id: 3,
          label: 'Насос',
          is_running: false,
          bound_plants: [{ id: 10, name: 'Розмарин', rate_ml_per_hour: 500 }],
        }],
      },
      assignments: [
        { zoneId: 7, zoneName: 'Ферма · Теплица 1', role: 'SOIL_MOISTURE_SENSOR' },
        { zoneId: 7, zoneName: 'Ферма · Теплица 1', role: 'WATER_PUMP' },
      ],
    });

    expect(screen.getByText('Роли в ферме')).toBeInTheDocument();
    expect(screen.getByText('Ферма · Теплица 1 · Влажность почвы')).toHaveAttribute('href', '/app/farm/');
    expect(screen.getByText('Ферма · Теплица 1 · Насос')).toHaveAttribute('href', '/app/farm/');
    expect(screen.queryByText('Розмарин')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Редактировать' })).not.toBeInTheDocument();
  });
});
