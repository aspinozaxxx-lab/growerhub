import { StrictMode } from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import { useAuth } from './features/auth/AuthContext';
import { METRIKA_ID } from './domain/siteConfig';

vi.mock('./components/layout/Layout', () => ({ default: ({ children }) => children }));
vi.mock('./pages/app/AppSection', () => ({
  default: function AppSection() {
    const { accountStatus, demoActive } = useAuth();
    return <output data-testid="auth-state">{accountStatus}:{String(demoActive)}</output>;
  },
}));

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

function LocationProbe() {
  const { pathname, search, hash, state } = useLocation();
  return <output data-testid="location">{JSON.stringify({ pathname, search, hash, state })}</output>;
}

const renderCallback = (entry) => render(
  <StrictMode>
    <MemoryRouter initialEntries={[entry]}>
      <App />
      <LocationProbe />
    </MemoryRouter>
  </StrictMode>,
);

beforeEach(() => {
  sessionStorage.clear();
  window.ym = vi.fn();
  window.gtag = vi.fn();
  vi.stubGlobal('fetch', vi.fn(async (url, options = {}) => {
    if (url === '/api/auth/refresh') return json({ access_token: 'account-token' });
    if (url === '/api/auth/me') return new Headers(options.headers).get('Authorization') === 'Bearer account-token'
      ? json({ id: 112, role: 'user', timezone: 'UTC' }) : json({}, 401);
    if (url === '/api/demo/refresh') return json({
      access_token: 'demo-token', space: { id: 'guest-demo', saved: false, timezone: 'UTC' },
    });
    throw new Error('Unexpected request: ' + url);
  }));
});

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  delete window.ym;
  delete window.gtag;
  vi.unstubAllGlobals();
});

it.each([
  { pathname: '/app/', search: '?signup=complete&lang=en', demo: false },
  { pathname: '/app/onboarding/', search: '?signup=complete&lang=en', demo: false },
  { pathname: '/app/demo/', search: '?save=1&signup=complete&lang=en', demo: true },
])('uchityvaet registraciyu posle SSO na $pathname odin raz i sohranyaet marshrut', async ({ pathname, search, demo }) => {
  if (demo) sessionStorage.setItem('gh_demo_active', '1');
  const state = { demoPlacement: 'demo_banner' };
  renderCallback({ pathname, search, hash: '#history', state });

  await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent(`authorized:${demo}`));
  await waitFor(() => expect(window.ym).toHaveBeenCalledWith(
    METRIKA_ID, 'reachGoal', 'signup_complete', expect.objectContaining({ step: 'sso_callback', mode: 'account' }),
  ));
  expect(window.ym.mock.calls.filter((call) => call[2] === 'signup_complete')).toHaveLength(1);
  expect(window.gtag.mock.calls.filter((call) => call[1] === 'signup_complete')).toHaveLength(1);
  expect(JSON.parse(screen.getByTestId('location').textContent)).toEqual({
    pathname, search: demo ? '?save=1&lang=en' : '?lang=en', hash: '#history', state,
  });
});

it('ne schitaet gostevuyu demosessiyu registraciej akkaunta', async () => {
  sessionStorage.setItem('gh_demo_active', '1');
  const defaultFetch = fetch.getMockImplementation();
  fetch.mockImplementation((url, options) => url.startsWith('/api/auth/')
    ? Promise.resolve(json({}, 401)) : defaultFetch(url, options));
  renderCallback('/app/demo/?signup=complete');

  await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent('unauthorized:true'));
  expect(window.ym.mock.calls.filter((call) => call[2] === 'signup_complete')).toHaveLength(0);
  expect(window.gtag.mock.calls.filter((call) => call[1] === 'signup_complete')).toHaveLength(0);
});

it('ne schitaet povtornyj vhod novoj registraciej', async () => {
  renderCallback('/app/?lang=en');

  await waitFor(() => expect(screen.getByTestId('auth-state')).toHaveTextContent('authorized:false'));
  expect(window.ym.mock.calls.filter((call) => call[2] === 'signup_complete')).toHaveLength(0);
  expect(window.gtag.mock.calls.filter((call) => call[1] === 'signup_complete')).toHaveLength(0);
  expect(JSON.parse(screen.getByTestId('location').textContent).search).toBe('?lang=en');
});
