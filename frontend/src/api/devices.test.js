import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, readApiErrorMessage } from './client';
import { claimDevice } from './devices';

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
});
