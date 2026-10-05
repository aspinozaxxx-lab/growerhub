import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './client';
import { createShopRequest } from './shop';

vi.mock('./client', () => ({ apiFetch: vi.fn() }));

describe('Shop API', () => {
  beforeEach(() => vi.resetAllMocks());

  it('sohranyaet HTTP413 pri HTML ot nginx bez vystavleniya ego kak setevogo timeout', async () => {
    apiFetch.mockResolvedValue(new Response('<html>413 Request Entity Too Large</html>', { status: 413, headers: { 'Content-Type': 'text/html' } }));
    await expect(createShopRequest({ kind: 'ORDER' })).rejects.toMatchObject({ status: 413, code: 'REQUEST_FAILED' });
  });
});
