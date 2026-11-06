import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import RequireAuth from './RequireAuth';
import AppLayout from '../../components/layout/AppLayout';
import AppDemoTools from '../../pages/app/AppDemoTools';
import AppDemo from '../../pages/app/AppDemo';
import AppOnboarding from '../../pages/app/AppOnboarding';
import LoginPage from '../../pages/app/LoginPage';
import { changeLocale, getCurrentLocale, loadAppTranslations } from '../../locales/i18n';

vi.mock('../assistant/AssistantEntry', () => ({ default: () => null }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));
vi.mock('../../domain/siteConfig', async original => ({ ...await original(), DEMO_PUBLIC_ENABLED: true, SELF_SERVICE_PUBLIC_ENABLED: true }));

let account;
const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
const demo = () => ({ access_token: 'test-demo-token', space: { id: 'synthetic-demo', saved: account, timezone: 'Europe/Moscow' } });
beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async url => {
    if (url === '/api/auth/me') return account ? json({ id: 1, role: 'user', timezone: 'Europe/Moscow' }) : json({}, 401);
    if (url === '/api/auth/refresh') return json({}, 401);
    if (url === '/api/demo/refresh' || url === '/api/demo/start') return json(demo());
    if (url === '/api/demo/status') return json({ devices: [], weather: 'sunny' });
    if (url === '/api/demo/catalog' || url === '/api/zigbee/coordinators') return json([]);
    if (url === '/api/onboarding/status') return json({ step: 'CREATE_COORDINATOR', coordinator_count: 0, completed: false });
    throw new Error('Unexpected request: ' + url);
  }));
});
afterEach(async () => {
  cleanup(); sessionStorage.clear(); localStorage.clear();
  vi.unstubAllGlobals(); vi.clearAllMocks();
  await changeLocale('ru', { remember: false });
});

function Workspace() {
  const { sessionKey } = useAuth();
  return <RequireAuth><AppLayout key={sessionKey} /></RequireAuth>;
}
function Location() {
  const location = useLocation();
  const { demoActive, status } = useAuth();
  return <><output data-testid="exit-location">{location.pathname + location.search}</output>
    <output data-testid="exit-auth">{demoActive ? 'demo' : 'account'}:{status}</output></>;
}
const cases = ['ru', 'en'].flatMap(locale => [false, true].flatMap(compact => [false, true]
  .flatMap(signedIn => ['demo-tools', 'overview'].flatMap(entry => (signedIn ? ['connect', 'farm'] : ['connect'])
    .map(action => [locale, compact, signedIn, entry, action])))));
it.each(cases)('vyhod bez povtornogo demo (%s compact=%s account=%s %s %s)', async (locale, compact, signedIn, entry, action) => {
  account = signedIn;
  sessionStorage.setItem('gh_demo_active', '1');
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: compact, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  await changeLocale(locale);
  await loadAppTranslations();
  const path = entry === 'demo-tools' ? '/app/demo-tools/' : '/app/';
  render(<MemoryRouter initialEntries={[path + '?lang=' + locale]}><AuthProvider>
    <Routes>
      <Route element={<Workspace />}>
        <Route path="/app/" element={<h2>Overview fixture</h2>} />
        <Route path="/app/demo-tools/" element={<AppDemoTools />} />
        <Route path="/app/onboarding/" element={<AppOnboarding />} />
      </Route>
      <Route path="/app/demo/" element={<AppDemo />} />
      <Route path="/app/login/" element={<LoginPage />} />
    </Routes>
    <Location />
  </AuthProvider></MemoryRouter>);
  await waitFor(() => expect(screen.getByTestId('exit-auth').textContent).toBe('demo:authorized'));
  if (compact) fireEvent.click(screen.getByRole('button', { name: /^(Демо|Demo)\./ }));
  const button = action === 'farm' ? (locale === 'ru' ? 'Моя ферма' : 'My farm')
    : (locale === 'ru' ? 'Подключить свои устройства' : 'Connect my devices');
  fireEvent.click(screen.getByRole('button', { name: button, exact: true }));
  await waitFor(() => expect(screen.getByTestId('exit-auth').textContent).toBe('account:' + (account ? 'authorized' : 'unauthorized')));
  const heading = action === 'farm' ? 'Overview fixture' : account ? (locale === 'ru' ? 'Дневник без оборудования' : 'A journal without equipment')
    : (locale === 'ru' ? 'Начать работу с GrowerHub' : 'Get started with GrowerHub');
  await screen.findByRole('heading', { name: heading, exact: true });
  const current = new URL(screen.getByTestId('exit-location').textContent, 'https://growerhub.test');
  const target = account ? current : new URL(current.searchParams.get('redirect'), 'https://growerhub.test');
  expect(target.pathname).toBe(action === 'farm' ? '/app/' : '/app/onboarding/');
  if (action === 'connect') expect(target.searchParams.get('lang')).toBe(locale);
  expect(getCurrentLocale()).toBe(locale);
  expect(sessionStorage.getItem('gh_demo_active')).toBeNull();
  expect(screen.getByTestId('exit-auth').textContent).toBe('account:' + (account ? 'authorized' : 'unauthorized'));
  expect(fetch.mock.calls.filter(([url]) => url === '/api/demo/start')).toHaveLength(0);
  expect(fetch.mock.calls.filter(([url, options]) => options?.method && options.method !== 'GET'
    && !['/api/auth/refresh', '/api/demo/refresh'].includes(url))).toEqual([]);
});
