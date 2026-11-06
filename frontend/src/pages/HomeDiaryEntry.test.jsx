import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import catalog from '../../../backend/src/main/resources/shop/catalog.json';
import HomePage from './HomePage';
import AppOnboarding from './app/AppOnboarding';
import LoginPage from './app/LoginPage';
import { AuthProvider, useAuth } from '../features/auth/AuthContext';
import RequireAuth from '../features/auth/RequireAuth';
import { changeLocale, getCurrentLocale, loadAppTranslations } from '../locales/i18n';

vi.mock('../features/shop', async () => ({
  ...await import('../features/shop/copy'),
  useShop: () => ({ catalog, cartLocked: false, addItem: vi.fn(), openConsultation: vi.fn() }),
}));
vi.mock('../domain/siteConfig', async (original) => ({
  ...await original(), SELF_SERVICE_PUBLIC_ENABLED: true, DEMO_PUBLIC_ENABLED: true,
}));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

let demoState;
beforeEach(() => {
  demoState = 'guest';
  let signedIn = false;
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    if (url === '/api/auth/refresh') return json({}, 401);
    if (url === '/api/demo/refresh') return demoState === 'active-demo'
      ? json({ access_token: 'test-demo-token', space: { id: 42, timezone: 'Europe/Moscow' } }) : json({}, 401);
    if (url === '/api/auth/login') {
      signedIn = true;
      return json({ access_token: 'test-account-token' });
    }
    if (url === '/api/auth/me') return signedIn
      ? json({ id: 1, role: 'user', timezone: 'Europe/Moscow' }) : json({}, 401);
    if (url === '/api/onboarding/status') return json({
      step: 'CREATE_COORDINATOR', coordinator_count: 0, coordinator_connected: false,
      first_device_seen: false, zone_created: false, completed: false,
    });
    if (url === '/api/zigbee/coordinators') return json([]);
    throw new Error('Unexpected request: ' + url);
  }));
});

afterEach(async () => {
  cleanup(); sessionStorage.clear(); localStorage.clear();
  vi.unstubAllGlobals(); vi.clearAllMocks();
  await changeLocale('ru', { remember: false });
});

function Location() {
  const location = useLocation();
  const { demoActive, status } = useAuth();
  return <><output data-testid="diary-location">{location.pathname + location.search}</output>
    <output data-testid="diary-auth">{demoActive ? 'demo' : 'account'}:{status}</output></>;
}

const locales = [
  ['ru', 'Вести дневник своего растения', 'Начать работу с GrowerHub', 'Вход по паролю для существующих аккаунтов', 'Электронная почта', 'Пароль', 'Войти', 'Дневник без оборудования', 'Вести дневник растений'],
  ['en', 'Keep a diary for your plant', 'Get started with GrowerHub', 'Password login for existing accounts', 'Email', 'Password', 'Login', 'A journal without equipment', 'Start a plant journal'],
];
it.each(locales.flatMap((copy) => ['guest', 'active-demo', 'expired-demo'].map((mode) => [mode, ...copy])))
('glavnaya sohranyaet lokal i tsel sobstvennogo dnevnika posle vhoda (%s, %s)', async (
  mode, locale, entryText, loginHeading, passwordSummary, emailLabel, passwordLabel, signIn, diaryHeading, diaryStart,
) => {
  demoState = mode;
  if (mode !== 'guest') sessionStorage.setItem('gh_demo_active', '1');
  await changeLocale(locale, { remember: false });
  await loadAppTranslations();
  const target = `/app/onboarding/?lang=${locale}`;
  render(<MemoryRouter initialEntries={[locale === 'en' ? '/en/' : '/']}><AuthProvider>
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/en/" element={<HomePage />} />
      <Route path="/app/login/" element={<LoginPage />} />
      <Route path="/app/onboarding/" element={<RequireAuth><AppOnboarding /></RequireAuth>} />
    </Routes>
    <Location />
  </AuthProvider></MemoryRouter>);

  const initialAuth = mode === 'guest' ? 'account:unauthorized'
    : mode === 'active-demo' ? 'demo:authorized' : 'demo:unauthorized';
  await waitFor(() => expect(screen.getByTestId('diary-auth').textContent).toBe(initialAuth));
  fireEvent.click(screen.getByRole('link', { name: entryText }));
  await screen.findByRole('heading', { name: loginHeading });
  const loginPath = screen.getByTestId('diary-location').textContent;
  expect(new URL(loginPath, 'https://growerhub.test').searchParams.get('redirect')).toBe(target);
  expect(getCurrentLocale()).toBe(locale);
  expect(screen.getByTestId('diary-auth').textContent).toBe('account:unauthorized');
  expect(sessionStorage.getItem('gh_demo_active')).toBeNull();

  fireEvent.click(screen.getByText(passwordSummary));
  fireEvent.change(screen.getByLabelText(emailLabel), { target: { value: 'qa@example.test' } });
  fireEvent.change(screen.getByLabelText(passwordLabel), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: signIn, exact: true }));
  await screen.findByRole('heading', { name: diaryHeading });
  expect(screen.getByTestId('diary-location').textContent).toBe(target);
  expect(screen.getByRole('button', { name: diaryStart })).toBeVisible();
  expect(getCurrentLocale()).toBe(locale);
  const writes = fetch.mock.calls.filter(([url, options]) => options?.method
    && options.method !== 'GET' && !['/api/auth/login', '/api/auth/refresh', '/api/demo/refresh'].includes(url));
  expect(writes).toEqual([]);
});
