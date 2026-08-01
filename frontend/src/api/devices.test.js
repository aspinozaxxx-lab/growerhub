import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, readApiErrorMessage } from './client';
import {
  claimDevice,
  fetchDeviceFirmware,
  triggerDeviceFirmwareUpdate,
} from './devices';

vi.mock('./client', () => ({
  apiFetch: vi.fn(),
  readApiErrorMessage: vi.fn(),
}));

describe('claimDevice', () => {
  beforeEach(() => {
    apiFetch.mockReset();
    readApiErrorMessage.mockReset();
  });

  it('otpravlyaet pechatnyi device_id', async () => {
    apiFetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ device_id: 'GROVIKA_040AB1' }),
    });

    await expect(claimDevice('GROVIKA_040AB1')).resolves.toEqual({
      device_id: 'GROVIKA_040AB1',
    });
    expect(apiFetch).toHaveBeenCalledWith('/api/devices/claim', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ device_id: 'GROVIKA_040AB1' }),
    });
  });

  it('peredaet Retry-After v oshibku 429', async () => {
    apiFetch.mockResolvedValue({
      ok: false,
      status: 429,
      headers: new Headers({ 'Retry-After': '3599' }),
    });
    readApiErrorMessage.mockResolvedValue('Слишком много попыток');

    await expect(claimDevice('GROVIKA_040AB1')).rejects.toMatchObject({
      message: 'Слишком много попыток',
      status: 429,
      retryAfterSeconds: 3599,
    });
  });

  it('proveryaet i zapuskaet poslednyuyu proshivku', async () => {
    apiFetch
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({ latest_version: 'grovika-2' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({ result: 'accepted' }),
      });

    await expect(fetchDeviceFirmware('GROVIKA_040AB1')).resolves.toEqual({
      latest_version: 'grovika-2',
    });
    await expect(triggerDeviceFirmwareUpdate('GROVIKA_040AB1')).resolves.toEqual({
      result: 'accepted',
    });
    expect(apiFetch).toHaveBeenNthCalledWith(
      1,
      '/api/device/GROVIKA_040AB1/firmware',
    );
    expect(apiFetch).toHaveBeenNthCalledWith(
      2,
      '/api/device/GROVIKA_040AB1/firmware/update',
      { method: 'POST' },
    );
  });
});
