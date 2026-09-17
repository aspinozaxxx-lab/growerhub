import { lazy, Suspense } from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import LoadErrorBoundary from './LoadErrorBoundary';

vi.mock('../../locales/i18n', () => ({
  translateCommon: (key) => ({
    'error.load.title': 'Не удалось открыть страницу',
    'error.load.description': 'Обновите страницу и попробуйте ещё раз. Если ошибка повторяется, проверьте подключение к интернету.',
    'error.load.reload': 'Обновить страницу',
  }[key]),
}));

afterEach(() => vi.restoreAllMocks());

it('pokazyvaet soderzhimoe pri uspeshnoj zagruzke', () => {
  render(<LoadErrorBoundary><h1>Ферма</h1></LoadErrorBoundary>);
  expect(screen.getByRole('heading', { name: 'Ферма' })).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('pri sboe zagruzki razdela predlagaet obnovlenie vmesto pustoj stranicy', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  const FailedPage = lazy(() => Promise.reject(new TypeError('Failed to fetch dynamically imported module')));

  render(<LoadErrorBoundary><Suspense fallback="Загрузка"><FailedPage /></Suspense></LoadErrorBoundary>);

  expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось открыть страницу');
  expect(screen.getByRole('button', { name: 'Обновить страницу' })).toBeVisible();
  expect(screen.queryByText('Загрузка')).not.toBeInTheDocument();
});
