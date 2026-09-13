import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';

vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
let finishLogout;
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  sessionStorage.setItem('gh_demo_active', '1');
  vi.stubGlobal('fetch', vi.fn(async (url, options = {}) => {
    if (url === '/api/auth/refresh') return json({ access_token: 'account-token' });
    if (url === '/api/auth/me') return new Headers(options.headers).get('Authorization') === 'Bearer account-token'
      ? json({ id: 1, role: 'user', timezone: 'UTC' }) : json({}, 401);
    if (url === '/api/demo/refresh') return json({ access_token: 'demo-token', space: { id: 'saved-demo', saved: true, timezone: 'UTC' } });
    if (url === '/api/auth/logout') return new Promise((resolve) => { finishLogout = resolve; });
    throw new Error('Unexpected request: ' + url);
  }));
});
afterEach(() => { cleanup(); sessionStorage.clear(); vi.unstubAllGlobals(); });

it('otzyvaet cookie pered ochistkoj osnovnoj i demo sessij', async () => {
  const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(result.current.status).toBe('authorized'));
  expect(result.current.accountStatus).toBe('authorized');
  let pending;
  act(() => { pending = result.current.logout(); });
  expect(fetch).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  expect(result.current.demoSession.saved).toBe(true);
  await act(async () => { finishLogout(json({})); await pending; });
  expect(result.current.demoActive).toBe(false);
  expect(result.current.demoSession).toBeNull();
  expect(result.current.accountUser).toBeNull();
  expect(result.current.token).toBeNull();
  expect(sessionStorage.getItem('gh_demo_active')).toBeNull();
});

it('ne pokazyvaet uspeshnyj vyhod pri oshibke otzyva cookie', async () => {
  const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(result.current.status).toBe('authorized'));
  await act(async () => {
    const pending = result.current.logout();
    finishLogout(json({}, 503));
    await expect(pending).rejects.toThrow();
  });
  expect(result.current.accountStatus).toBe('authorized');
  expect(result.current.demoSession.saved).toBe(true);
});
