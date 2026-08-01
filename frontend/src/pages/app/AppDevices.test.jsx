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
import AppDevices from './AppDevices';

vi.mock('../../api/devices', () => ({
  claimDevice: vi.fn(),
  fetchDeviceFirmware: vi.fn(),
  fetchMyDevices: vi.fn(),
  triggerDeviceFirmwareUpdate: vi.fn(),
}));

vi.mock('../../api/plants', () => ({
  fetchPlants: vi.fn().mockResolvedValue([]),
}));

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ token: 'test-token' }),
}));

vi.mock('../../features/watering/WateringSidebarContext', () => ({
  useWateringSidebar: () => ({ refreshVersion: 0 }),
}));

vi.mock('../../components/devices/DeviceCard', () => ({
  default: ({ device, firmwareStatus, onFirmwareUpdate }) => (
    <div data-testid="device-card">
      <span>{device.device_id}</span>
      <span>{firmwareStatus?.status || 'NO_STATUS'}</span>
      {firmwareStatus?.update_available ? (
        <button type="button" onClick={onFirmwareUpdate}>Обновить прошивку</button>
      ) : null}
    </div>
  ),
}));

vi.mock('../../components/devices/EditDeviceModal', () => ({
  default: () => null,
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
    triggerDeviceFirmwareUpdate.mockReset();
    fetchMyDevices.mockResolvedValue([]);
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
});
