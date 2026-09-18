import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeAll, expect, it, vi } from 'vitest';
import AppLayout from './AppLayout';
import { changeLocale, loadAppTranslations } from '../../locales/i18n';

const auth = vi.hoisted(() => ({ demoActive: true }));
vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../features/auth/DemoBanner', () => ({ default: () => null }));

beforeAll(loadAppTranslations);
afterEach(async () => {
  cleanup();
  document.head.replaceChildren();
  auth.demoActive = true;
  await changeLocale('ru');
});

it.each([
  ['ru', 'Ручной полив — Демоферма · GrowerHub', 'Конструктор фермы', 'Конструктор фермы — Демоферма · GrowerHub'],
  ['en', 'Manual watering — Demo farm · GrowerHub', 'Farm Builder', 'Farm Builder — Demo farm · GrowerHub'],
])('obnovlyaet nazvanie vkladki pri navigacii v demo na yazyke %s', async (locale, initialTitle, link, nextTitle) => {
  await changeLocale(locale);
  render(<MemoryRouter initialEntries={['/app/manual-watering/']}><Routes>
    <Route path="/app/*" element={<AppLayout />} />
  </Routes></MemoryRouter>);
  expect(document.title).toBe(initialTitle);
  expect(document.head.querySelector('meta[name="robots"]').content).toBe('noindex,nofollow');
  fireEvent.click(screen.getAllByRole('link', { name: link, exact: true })[0]);
  expect(document.title).toBe(nextTitle);
  expect(document.head.querySelector('link[rel="canonical"]')).toBeNull();
});

it('ne vklyuchaet chastnye dannye rasteniya ili priznak demo v nazvanie obychnogo kabineta', async () => {
  auth.demoActive = false;
  await changeLocale('ru');
  render(<MemoryRouter initialEntries={['/app/plants/private-plant-id/journal/']}><Routes>
    <Route path="/app/*" element={<AppLayout />} />
  </Routes></MemoryRouter>);
  expect(document.title).toBe('Журнал растения — GrowerHub');
  expect(document.head.querySelector('meta[name="robots"]').content).toBe('noindex,nofollow');
});
