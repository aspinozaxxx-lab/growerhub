import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import catalog from '../../../../backend/src/main/resources/shop/catalog.json';
import HomePage from '../../pages/HomePage';
import EquipmentIndexPage from '../../pages/EquipmentIndexPage';
import GettingStartedPage from '../../pages/GettingStartedPage';
import { changeLocale } from '../../locales/i18n';

const { state, addItem, openConsultation } = vi.hoisted(() => ({
  state: { cartLocked: false }, addItem: vi.fn(), openConsultation: vi.fn(),
}));
vi.mock('../../features/shop', async () => ({
  ...await import('../../features/shop/copy'),
  useShop: () => ({ catalog, cartLocked: state.cartLocked, addItem, openConsultation }),
}));
vi.mock('../../domain/siteConfig', async (importOriginal) => ({ ...await importOriginal(), DEMO_PUBLIC_ENABLED: true }));
vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ demoActive: false, leaveDemo: vi.fn() }) }));

beforeEach(() => { addItem.mockReturnValue(true); state.cartLocked = false; });
afterEach(async () => { cleanup(); vi.clearAllMocks(); await changeLocale('ru', { remember: false }); });

it.each([
  ['ru', 'Больше времени на любимые растения', 'Состав и цена: Первый стеллаж', '/oborudovanie/#light-mini', 'Помогите выбрать'],
  ['en', 'More time for the plants you love', 'Contents and price: First growing shelf', '/en/equipment/#light-mini', 'Help me choose'],
])('glavnaya vedet k sostavu nabora i dostupnoj pomoschi (%s)', async (locale, heading, kitLink, target, help) => {
  await changeLocale(locale, { remember: false });
  render(<MemoryRouter><HomePage /></MemoryRouter>);
  expect(screen.getByRole('heading', { level: 1, name: heading })).toBeVisible();
  expect(screen.getByRole('link', { name: kitLink })).toHaveAttribute('href', target);
  expect(screen.getAllByRole('article')).toHaveLength(2);
  const demo = screen.getByRole('link', { name: locale === 'en' ? 'Try without hardware' : 'Попробовать без оборудования' });
  expect(demo).toHaveAttribute('href', `/app/demo/?lang=${locale}`);
  fireEvent.click(screen.getByRole('button', { name: help }));
  expect(openConsultation).toHaveBeenCalledTimes(1);
  expect(screen.getByText(locale === 'en' ? 'Mini · a pilot, to be checked before supply' : 'Mini · пока пилот, проверим перед поставкой')).toBeVisible();
  if (locale === 'en') expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
});

it('katalog dobavlyaet imenno vybrannyj nabor i vedet v korzinu', () => {
  render(<MemoryRouter><EquipmentIndexPage /></MemoryRouter>);
  const three = document.getElementById('light-three');
  expect(within(three).getByText('5 490 ₽', { exact: true })).toBeInTheDocument();
  fireEvent.click(within(three).getByRole('button', { name: 'Добавить в корзину: Три зоны света' }));
  expect(addItem).toHaveBeenCalledWith('light-three');
  expect(within(three).getByRole('status')).toHaveTextContent('Добавлено в корзину');
  expect(within(three).getByRole('link', { name: 'Перейти в корзину' })).toHaveAttribute('href', '/korzina/');
});

it('pri neyasnom rezultate zakaza drugoj nabor ne dobavlyaetsya', () => {
  state.cartLocked = true;
  render(<MemoryRouter><EquipmentIndexPage /></MemoryRouter>);
  const add = screen.getByRole('button', { name: 'Добавить в корзину: Первый стеллаж' });
  expect(add).toBeDisabled();
  fireEvent.click(add);
  expect(addItem).not.toHaveBeenCalled();
});

it('Mini ne vydajotsya za fizicheski proverennoe ustrojstvo', () => {
  render(<MemoryRouter><EquipmentIndexPage /></MemoryRouter>);
  const mini = document.getElementById('light-mini');
  expect(within(mini).getByText(/Подключение Mini к GrowerHub ещё требует проверки/u)).toBeInTheDocument();
  expect(screen.getByText(/Найдите удобный пункт выдачи на сайте СДЭК/u)).toBeVisible();
});

it('nachalo bez oborudovaniya vedet v sushchestvuyushchee demo', () => {
  render(<MemoryRouter><GettingStartedPage /></MemoryRouter>);
  expect(screen.getByRole('link', { name: 'Открыть демоферму' })).toHaveAttribute('href', '/app/demo/?lang=ru');
  expect(screen.getByRole('link', { name: 'Выбрать комплект' })).toHaveAttribute('href', '/oborudovanie/');
  expect(screen.getByRole('heading', { name: 'Как подключим ваш комплект' })).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Помогите начать' }));
  expect(openConsultation).toHaveBeenCalledTimes(1);
});

it('anglijskij katalog sohranyaet cenu i ne podmeshivaet russkie razdely', async () => {
  await changeLocale('en', { remember: false });
  render(<MemoryRouter><EquipmentIndexPage /></MemoryRouter>);
  expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
  const white = document.getElementById('light-white');
  expect(within(white).getByRole('heading', { level: 2 })).toHaveTextContent('More signal headroom');
  expect(within(white).getByText('RUB 4,490')).toBeInTheDocument();
  fireEvent.click(within(white).getByRole('button', { name: 'Add to basket: More signal headroom' }));
  expect(within(white).getByRole('link', { name: 'View basket' })).toHaveAttribute('href', '/en/cart/');
});
