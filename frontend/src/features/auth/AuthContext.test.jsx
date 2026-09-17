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

it.each([200, 503])('pozdnij nachalnyj refresh %s ne sbrasyvaet uspeshno sohranennoe demo', async (status) => {
  const defaultFetch = fetch.getMockImplementation();
  let finishRefresh;
  const savedSpace = { id: 'guest-demo', saved: true, timezone: 'UTC' };
  fetch.mockImplementation((url, options) => {
    if (url === '/api/demo/refresh') return new Promise((resolve) => { finishRefresh = resolve; });
    if (url === '/api/demo/save') return Promise.resolve(json({ access_token: 'saved-demo-token', space: savedSpace }));
    return defaultFetch(url, options);
  });
  const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(finishRefresh).toBeTypeOf('function'));
  expect(result.current.accountStatus).toBe('authorized');
  expect(result.current.status).toBe('loading');

  await act(async () => { expect(await result.current.saveDemo()).toEqual({ success: true }); });
  expect(result.current.status).toBe('authorized');
  await act(async () => {
    finishRefresh(json({ access_token: 'old-guest-token', space: { ...savedSpace, saved: false } }, status));
  });

  expect(result.current.status).toBe('authorized');
  expect(result.current.token).toBe('saved-demo-token');
  expect(result.current.demoSession).toEqual(savedSpace);
  expect(result.current.demoActive).toBe(true);
  expect(result.current.accountStatus).toBe('authorized');
  expect(result.current.accountUser.id).toBe(1);
});

it('neuspeshnyj nachalnyj refresh bez novoj sessii zavershaet demo', async () => {
  const defaultFetch = fetch.getMockImplementation();
  fetch.mockImplementation((url, options) => url === '/api/demo/refresh'
    ? Promise.resolve(json({}, 503)) : defaultFetch(url, options));
  const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(result.current.status).toBe('unauthorized'));
  expect(result.current.accountStatus).toBe('authorized');
  expect(result.current.demoSession).toBeNull();
});

it('nedostupnaya demo-cookie pri sohranenii ne sbrasyvaet akkaunt i ne obnovlyaet ego token', async () => {
  const defaultFetch = fetch.getMockImplementation();
  fetch.mockImplementation((url, options) => url === '/api/demo/save'
    ? Promise.resolve(json({ detail: 'Demo session unavailable' }, 410)) : defaultFetch(url, options));
  const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
  await waitFor(() => expect(result.current.status).toBe('authorized'));
  fetch.mockClear();
  await act(async () => { expect(await result.current.saveDemo()).toEqual({ success: false, status: 410 }); });
  expect(result.current.accountStatus).toBe('authorized');
  expect(result.current.accountUser.id).toBe(1);
  expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/demo/save']);
});
