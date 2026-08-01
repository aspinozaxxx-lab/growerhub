import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  claimDevice,
  fetchDeviceFirmware,
  fetchMyDevices,
  triggerDeviceFirmwareUpdate,
} from '../../api/devices';
import { fetchFarmsOverview } from '../../api/selfService';
import AppDevices from './AppDevices';

vi.mock('../../api/devices', () => ({
  claimDevice: vi.fn(),
  fetchDeviceFirmware: vi.fn(),
  fetchMyDevices: vi.fn(),
  triggerDeviceFirmwareUpdate: vi.fn(),
}));

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
}));

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ token: 'test-token' }),
}));

vi.mock('../../features/watering/WateringSidebarContext', () => ({
  useWateringSidebar: () => ({ refreshVersion: 0 }),
}));

vi.mock('../../components/devices/DeviceCard', () => ({
  default: ({ device, assignments, firmwareStatus, onFirmwareUpdate }) => (
    <div data-testid="device-card">
      <span>{device.device_id}</span>
      {assignments.map((assignment) => (
        <span key={`${assignment.zoneId}:${assignment.role}`}>
          {assignment.zoneName} · {assignment.role}
        </span>
      ))}
      <span>{firmwareStatus?.status || 'NO_STATUS'}</span>
      {firmwareStatus?.update_available ? (
        <button type="button" onClick={onFirmwareUpdate}>Обновить прошивку</button>
      ) : null}
    </div>
  ),
}));

vi.mock('./AppZigbeeDevices', () => ({
  default: ({ embedded }) => (
    <div data-testid="zigbee-devices-section" data-embedded={String(embedded)} />
  ),
}));

function rejectedClaim(status, message, retryAfterSeconds = null) {
  const error = new Error(message);
  error.status = status;
  error.retryAfterSeconds = retryAfterSeconds;
  return error;
}

describe('AppDevices', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimDevice.mockReset();
    fetchDeviceFirmware.mockReset();
    fetchMyDevices.mockReset();
    fetchFarmsOverview.mockReset();
    triggerDeviceFirmwareUpdate.mockReset();
    fetchMyDevices.mockResolvedValue([]);
    fetchFarmsOverview.mockResolvedValue({ farms: [], resource_catalog: {} });
    fetchDeviceFirmware.mockResolvedValue({ update_available: false, status: 'IDLE' });
  });

  afterEach(() => {
    cleanup();
  });

  it('privyazyvaet ustrojstvo po serialnomu ID i obnovlyaet spisok', async () => {
    fetchMyDevices
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ id: 4, device_id: 'GROVIKA_040AB1' }]);
    claimDevice.mockResolvedValue({ id: 4, device_id: 'GROVIKA_040AB1' });
    render(<AppDevices />);

    const input = await screen.findByRole('textbox', { name: 'ID устройства' });
    fireEvent.change(input, { target: { value: 'grovika_040ab1' } });
    expect(input).toHaveValue('GROVIKA_040AB1');
    fireEvent.click(screen.getByRole('button', { name: 'Добавить устройство' }));

    await waitFor(() => {
      expect(claimDevice).toHaveBeenCalledWith('GROVIKA_040AB1', 'test-token');
      expect(fetchMyDevices).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByText('Устройство GROVIKA_040AB1 добавлено')).toBeInTheDocument();
    expect(screen.getByTestId('device-card')).toHaveTextContent('GROVIKA_040AB1');
    expect(screen.getByTestId('zigbee-devices-section')).toHaveAttribute('data-embedded', 'true');
  });

  it('pokazyvaet otdelnye sostoyaniya 404 i 409', async () => {
    claimDevice
      .mockRejectedValueOnce(rejectedClaim(404, 'Устройство не найдено'))
      .mockRejectedValueOnce(rejectedClaim(
        409,
        'Устройство уже используется другим пользователем — обратитесь к администратору',
      ));
    render(<AppDevices />);

    const input = await screen.findByRole('textbox', { name: 'ID устройства' });
    fireEvent.change(input, { target: { value: 'GROVIKA_FFFFFF' } });
    fireEvent.click(screen.getByRole('button', { name: 'Добавить устройство' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Устройство с таким ID не найдено');

    fireEvent.change(input, { target: { value: 'GROVIKA_040AB1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Добавить устройство' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Устройство уже используется другим пользователем — обратитесь к администратору',
    );
  });

  it('pokazyvaet Retry-After i blokiruet formu do okonchaniya pauzy', async () => {
    claimDevice.mockRejectedValue(rejectedClaim(429, 'Слишком много попыток', 3600));
    render(<AppDevices />);

    const input = await screen.findByRole('textbox', { name: 'ID устройства' });
    fireEvent.change(input, { target: { value: 'GROVIKA_FFFFFF' } });
    fireEvent.click(screen.getByRole('button', { name: 'Добавить устройство' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Лимит попыток исчерпан');
    expect(screen.getByText('Повторить через 60 мин.')).toBeInTheDocument();
    expect(input).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Добавить устройство' })).toBeDisabled();
  });

  it('pokazyvaet novuyu proshivku i zapuskaet OTA', async () => {
    fetchMyDevices.mockResolvedValue([{
      id: 7,
      device_id: 'GROVIKA_040AB1',
      is_online: true,
    }]);
    fetchDeviceFirmware
      .mockResolvedValueOnce({
        update_available: true,
        current_version: 'grovika-1',
        latest_version: 'grovika-2',
        status: 'IDLE',
      })
      .mockResolvedValue({
        update_available: true,
        current_version: 'grovika-1',
        latest_version: 'grovika-2',
        target_version: 'grovika-2',
        status: 'QUEUED',
      });
    triggerDeviceFirmwareUpdate.mockResolvedValue({
      result: 'accepted',
      version: 'grovika-2',
    });

    render(<AppDevices />);

    fireEvent.click(await screen.findByRole('button', { name: 'Обновить прошивку' }));
    await waitFor(() => {
      expect(triggerDeviceFirmwareUpdate).toHaveBeenCalledWith('GROVIKA_040AB1', 'test-token');
      expect(fetchDeviceFirmware).toHaveBeenCalledTimes(2);
    });
    expect(screen.getByTestId('device-card')).toHaveTextContent('QUEUED');
  });

  it('peredajot v kartochku roli Grovika iz slotov teplicy', async () => {
    fetchMyDevices.mockResolvedValue([{
      id: 7,
      device_id: 'GROVIKA_040AB1',
      sensors: [{ id: 21 }],
      pumps: [{ id: 31 }],
    }]);
    fetchFarmsOverview.mockResolvedValue({
      farms: [{
        id: 1,
        name: 'Ферма',
        greenhouses: [{
          id: 2,
          name: 'Теплица',
          slots: [{
            role: 'WATER_PUMP',
            source_type: 'NATIVE_PUMP',
            native_pump_id: 31,
          }],
        }],
      }],
    });

    render(<AppDevices />);

    expect(await screen.findByTestId('device-card'))
      .toHaveTextContent('Ферма · Теплица · WATER_PUMP');
  });
});
