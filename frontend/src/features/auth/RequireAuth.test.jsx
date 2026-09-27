import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthContext';
import RequireAuth from './RequireAuth';
import LoginPage from '../../pages/app/LoginPage';
import PushokPilot from '../../components/PushokPilot';

vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn(), trackProductGoalOnce: vi.fn() }));

const pilotPath = '/app/settings/connections/?pilot=pushok&lang=ru';
const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

beforeEach(() => {
  let signedIn = false;
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    if (url === '/api/auth/refresh' || url === '/api/demo/refresh') return json({}, 401);
    if (url === '/api/auth/login') {
      signedIn = true;
      return json({ access_token: 'test-account-token' });
    }
    if (url === '/api/auth/me') return signedIn
      ? json({ id: 1, role: 'user', timezone: 'UTC' }) : json({}, 401);
    if (url === '/api/users/me/pushok-pilot') return json({ request: null });
    throw new Error('Unexpected request: ' + url);
  }));
});

afterEach(() => { cleanup(); sessionStorage.clear(); localStorage.clear(); vi.unstubAllGlobals(); });

function Location() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

function entry(url) {
  render(<MemoryRouter initialEntries={[url]}><AuthProvider>
    <Routes>
      <Route path="/app/login/" element={<LoginPage />} />
      <Route path="/app/demo/" element={<div>Expired demo</div>} />
      <Route path="/app/settings/connections/" element={<RequireAuth><PushokPilot /></RequireAuth>} />
    </Routes>
    <Location />
  </AuthProvider></MemoryRouter>);
}

it.each([false, true])('vozvrashchaet v zayavku PushOk posle vhoda, perezagruzka %s', async (reload) => {
  entry(pilotPath);
  await screen.findByRole('heading', { name: 'Начать работу с GrowerHub' });
  const loginPath = screen.getByTestId('location').textContent;
  expect(new URL(loginPath, 'https://growerhub.test').searchParams.get('redirect')).toBe(pilotPath);

  if (reload) {
    cleanup();
    entry(loginPath);
    await screen.findByRole('heading', { name: 'Начать работу с GrowerHub' });
  }

  fireEvent.click(screen.getByText('Вход по паролю для существующих аккаунтов'));
  fireEvent.change(screen.getByLabelText('Электронная почта'), { target: { value: 'qa@example.test' } });
  fireEvent.change(screen.getByLabelText('Пароль'), { target: { value: 'test-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Войти', exact: true }));

  await screen.findByRole('button', { name: 'Хочу участвовать' });
  expect(screen.getByTestId('location').textContent).toBe(pilotPath);
  fireEvent.click(screen.getByRole('button', { name: 'Хочу участвовать' }));
  expect(screen.getByLabelText('Контакт для ответа')).toBeInTheDocument();
  expect(fetch.mock.calls.some(([, options]) => options?.method === 'PUT')).toBe(false);
});

it('istekshaya demosessiya vedet k obnovleniyu demo bez vhoda v akkaunt', async () => {
  sessionStorage.setItem('gh_demo_active', '1');
  entry(pilotPath);
  await screen.findByText('Expired demo');
  await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/app/demo/?expired=1'));
  expect(screen.queryByRole('heading', { name: 'Начать работу с GrowerHub' })).not.toBeInTheDocument();
});
