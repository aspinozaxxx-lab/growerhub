import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { changeLocale, getStoredLocale } from '../locales/i18n';
import GettingStartedPage from './GettingStartedPage';

const { leaveDemo } = vi.hoisted(() => ({ leaveDemo: vi.fn() }));
vi.mock('../features/auth/AuthContext', () => ({ useAuth: () => ({ demoActive: true, leaveDemo }) }));
vi.mock('../utils/analytics', () => ({ trackProductGoal: vi.fn() }));
vi.mock('../features/shop', () => ({ useShop: () => ({ openConsultation: vi.fn() }) }));

function Location() {
  return <output data-testid="location">{useLocation().search}</output>;
}

afterEach(async () => { cleanup(); vi.clearAllMocks(); localStorage.clear(); await changeLocale('ru', { remember: false }); });

it.each([
  ['ru', 'Хочу попробовать ПушОк с GrowerHub'],
  ['en', 'Try PushOk with GrowerHub'],
])('vyhod iz demo sohranyaet pilot i yazyk %s posle vhoda', async (locale, cta) => {
  await changeLocale(locale, { remember: false });
  render(<MemoryRouter><GettingStartedPage /><Location /></MemoryRouter>);
  if (locale === 'en') expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
  fireEvent.click(screen.getByText(locale === 'en' ? 'Already have PushOk, Home Assistant or other hardware?' : 'Уже есть ПушОк, Home Assistant или другое оборудование?'));
  fireEvent.click(screen.getByRole('link', { name: cta }));
  expect(leaveDemo).toHaveBeenCalledTimes(1);
  const params = new URLSearchParams(screen.getByTestId('location').textContent);
  expect(params.get('redirect')).toBe(`/app/settings/connections/?pilot=pushok&lang=${locale}`);
  expect(getStoredLocale()).toBe(locale);
});

it('staryj yakor pushok raskryvaet podrobnuyu instrukciyu', () => {
  render(<MemoryRouter initialEntries={['/kak-nachat/#pushok']}><GettingStartedPage /></MemoryRouter>);
  const section = document.getElementById('pushok');
  expect(section).toBeVisible();
  expect(section.closest('details')).toHaveAttribute('open');
});
