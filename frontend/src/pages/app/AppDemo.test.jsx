import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import AppDemo from './AppDemo';
import { trackProductGoal } from '../../utils/analytics';
import { changeLocale, getStoredLocale, LOCALE_STORAGE_KEY } from '../../locales/i18n';

const auth = vi.hoisted(() => ({
  accountStatus: 'unauthorized',
  startDemo: vi.fn(),
  saveDemo: vi.fn(),
  leaveDemo: vi.fn(),
}));
vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
afterEach(async () => {
  cleanup(); vi.resetAllMocks(); auth.accountStatus = 'unauthorized';
  localStorage.removeItem(LOCALE_STORAGE_KEY);
  await changeLocale('ru', { remember: false });
});

function Destination() {
  const location = useLocation();
  return <p data-testid="destination">{location.pathname}{location.search}</p>;
}

function entry(search) {
  render(<MemoryRouter initialEntries={[{ pathname: '/app/demo/', search, state: { demoPlacement: 'article_intro_demo' } }]}>
    <Routes><Route path="/app/demo/" element={<AppDemo />} /><Route path="*" element={<Destination />} /></Routes>
  </MemoryRouter>);
}

it.each([
  ['automations', '/app/automations/'],
  ['watering', '/app/manual-watering/'],
  ['plants', '/app/plants/'],
])('otkryvaet vybrannyj razdel %s tolko posle uspeshnogo starta demo', async (view, path) => {
  auth.startDemo.mockResolvedValue({ success: true });
  entry(`?view=${view}`);
  await waitFor(() => expect(screen.getByTestId('destination')).toHaveTextContent(path));
  expect(auth.startDemo).toHaveBeenCalledTimes(1);
  expect(auth.saveDemo).not.toHaveBeenCalled();
  expect(trackProductGoal).toHaveBeenCalledWith('demo_open', { placement: 'article_intro_demo', action: view });
});

it.each(['https://example.invalid/', '/app/admin/', '__proto__'])('ne ispolzuet proizvolnyj adres %s kak marshrut', async (view) => {
  auth.startDemo.mockResolvedValue({ success: true });
  entry(`?view=${encodeURIComponent(view)}`);
  await waitFor(() => expect(screen.getByTestId('destination').textContent).toBe('/app/'));
});

it('ostavlyaet polzovatelya na oshibke i sohranyaet vybor pri povtore', async () => {
  auth.startDemo.mockResolvedValueOnce({ success: false, status: 429 }).mockResolvedValueOnce({ success: true });
  entry('?view=farm');
  expect(await screen.findByRole('alert')).toBeInTheDocument();
  expect(screen.queryByTestId('destination')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Открыть демоферму' }));
  await waitFor(() => expect(screen.getByTestId('destination')).toHaveTextContent('/app/farm/'));
});

it('sohranyaet obychnyj vhod v akkaunt dlya sohraneniya demo', async () => {
  entry('?save=1');
  await waitFor(() => expect(screen.getByTestId('destination')).toHaveTextContent('/app/login/?redirect=%2Fapp%2Fdemo%2F%3Fsave%3D1'));
  expect(auth.startDemo).not.toHaveBeenCalled();
  expect(auth.saveDemo).not.toHaveBeenCalled();
});

it('sohranyaet anglijskij jazyk publichnoj stranicy pri vhode v demo', async () => {
  localStorage.setItem(LOCALE_STORAGE_KEY, 'ru');
  await changeLocale('en', { remember: false });
  auth.startDemo.mockResolvedValue({ success: true });
  entry('?lang=en&view=automations');
  await waitFor(() => expect(screen.getByTestId('destination')).toHaveTextContent('/app/automations/'));
  expect(getStoredLocale()).toBe('en');
});
