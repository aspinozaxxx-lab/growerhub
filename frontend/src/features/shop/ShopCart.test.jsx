import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import snapshot from '../../../../backend/src/main/resources/shop/catalog.json';
import { createShopRequest, fetchShopCatalog } from '../../api/shop';
import { changeLocale } from '../../locales/i18n';
import { trackProductGoal } from '../../utils/analytics';
import ShopProvider from './ShopProvider';
import ShopCart from './ShopCart';
import { useShop } from './ShopContext';
import { CART_STORAGE_KEY } from './model';

vi.mock('../../api/shop', () => ({ createShopRequest: vi.fn(), fetchShopCatalog: vi.fn() }));
vi.mock('../../utils/analytics', () => ({ trackProductGoal: vi.fn() }));

const receipt = { number: 'GH-20261005-12', totalMinor: 329000, currency: 'RUB', createdAt: '2026-10-05T12:00:00', status: 'NEW' };
const catalog = { ...snapshot, acceptingRequests: true };

function Harness({ cart = true }) {
  const { addItem, openConsultation, cartCount } = useShop();
  return <><button onClick={() => addItem('light-mini')}>Add Mini</button><button onClick={openConsultation}>Ask for help</button><output aria-label="cart count">{cartCount}</output>{cart ? <ShopCart /> : null}</>;
}
const show = (options = {}) => render(<MemoryRouter initialEntries={[options.path || '/korzina/']}><ShopProvider><Harness cart={options.cart !== false} /></ShopProvider></MemoryRouter>);
const fill = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
const fillContacts = () => {
  fill('Ваше имя', 'Анна'); fill('Телефон', '+7 999 123-45-67');
};
const fillOrder = () => {
  fillContacts(); fill('Город', 'Тверь'); fill('Код пункта СДЭК', 'TVR1'); fill('Адрес пункта СДЭК', 'ул. Садовая, 10');
  fireEvent.click(screen.getByLabelText('Согласен на обработку данных для связи и выполнения заявки.'));
};
const addMini = async () => {
  fireEvent.click(screen.getByRole('button', { name: 'Add Mini' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Оформить заказ' })).toBeEnabled());
};

describe('Shop checkout', () => {
  beforeEach(async () => {
    vi.resetAllMocks(); window.localStorage.clear();
    await changeLocale('ru', { remember: false });
    fetchShopCatalog.mockResolvedValue(catalog);
    createShopRequest.mockResolvedValue(receipt);
  });
  afterEach(cleanup);

  it('sohranyaet vse shest pozicij i otpravlyaet varianty nasosa bez podmeny sostava', async () => {
    const items = ['light-mini', 'light-white', 'light-three', 'soil-sensor', 'pump', 'pump-drip-kit'].map((offerId) => ({ offerId, quantity: 1 }));
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    show();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Оформить заказ' })).toBeEnabled());
    expect(screen.getAllByLabelText('Количество')).toHaveLength(6);
    expect(screen.getByLabelText('cart count')).toHaveTextContent('6');
    expect(screen.getByText('18 267 ₽')).toBeVisible();
    expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY))).toEqual(items);
    fillOrder();
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    await screen.findByText('Заказ получен');
    expect(createShopRequest.mock.calls[0][0].items).toEqual(items);
  });

  it('vosstanavlivaet tolko sostav korziny i ne hranit postoronnie dannye', async () => {
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify([
      { offerId: 'light-mini', quantity: 2, name: 'Chuzhoe imya', phone: '1234567890' },
      { offerId: 'light-mini', quantity: 1 }, { offerId: '../../bad', quantity: 1 }, { offerId: 'light-white', quantity: -2 },
    ]));
    show();
    await waitFor(() => expect(screen.getByLabelText('cart count')).toHaveTextContent('3'));
    expect(screen.getByLabelText('Количество')).toHaveValue('3');
    expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY))).toEqual([{ offerId: 'light-mini', quantity: 3 }]);
    fireEvent.change(screen.getByLabelText('Количество'), { target: { value: '2' } });
    expect(screen.getByLabelText('cart count')).toHaveTextContent('2');
    fireEvent.click(screen.getByRole('button', { name: 'Убрать: Первый стеллаж' }));
    expect(screen.getByText('Выберите, с чего начать')).toBeInTheDocument();
  });

  it('oformlyaet gostevoj zakaz s ruchnym PVZ, tolko servernoj cenoj i bez PII v storage ili analytics', async () => {
    show(); await addMini();
    fillContacts();
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    expect(createShopRequest).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Город')).toHaveFocus();
    fillOrder(); fill('Telegram — необязательно', '@annas_garden');
    expect(screen.getByLabelText('Адрес пункта СДЭК')).toHaveClass('ym-disable-keys');
    expect(screen.getByLabelText('Адрес пункта СДЭК').closest('form')).toHaveClass('ym-hide-content');
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    expect(await screen.findByText('Заказ получен')).toBeInTheDocument();
    const payload = createShopRequest.mock.calls[0][0];
    expect(payload).toMatchObject({ kind: 'ORDER', catalogVersion: snapshot.version, items: [{ offerId: 'light-mini', quantity: 1 }], customer: { name: 'Анна', phone: '+7 999 123-45-67', telegram: '@annas_garden' }, pickup: { city: 'Тверь', code: 'TVR1', address: 'ул. Садовая, 10' }, consent: true, website: '' });
    expect(payload.idempotencyKey).toMatch(/^[a-f\d-]{36}$/u);
    expect(payload).not.toHaveProperty('totalMinor'); expect(payload.items[0]).not.toHaveProperty('priceMinor');
    expect(screen.getByLabelText('cart count')).toHaveTextContent('0');
    await waitFor(() => expect(window.localStorage.getItem(CART_STORAGE_KEY)).toBe('[]'));
    expect(JSON.stringify(trackProductGoal.mock.calls)).not.toMatch(/Анна|999|annas_garden|Садовая|TVR1/u);
    expect(screen.getByText('Свяжемся с вами, подтвердим комплект и доставку. Сейчас оплачивать ничего не нужно.')).toBeInTheDocument();
  });

  it('ne dubliroet otpravku pri dvojnom submit', async () => {
    let complete;
    createShopRequest.mockImplementation(() => new Promise((resolve) => { complete = resolve; }));
    show(); await addMini(); fillOrder();
    const form = screen.getByRole('button', { name: 'Оформить заказ' }).closest('form');
    fireEvent.submit(form); fireEvent.submit(form);
    expect(createShopRequest).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Сохраняем…' })).toBeDisabled();
    await act(async () => complete(receipt));
    expect(await screen.findByText('Заказ получен')).toBeInTheDocument();
  });

  it('posle neyasnoj seti povtoryaet imenno tot zhe zapros i blokiruet izmenenie dannyh', async () => {
    createShopRequest.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    show(); await addMini(); fillOrder();
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    const retry = await screen.findByRole('button', { name: 'Повторить проверку заказа' });
    expect(screen.getByRole('alert')).toHaveTextContent('Заявка уже могла сохраниться');
    expect(screen.getByLabelText('Телефон')).toHaveAttribute('readonly');
    expect(screen.getByLabelText('Количество')).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Add Mini' }));
    expect(screen.getByLabelText('cart count')).toHaveTextContent('1');
    fireEvent.click(retry);
    expect(await screen.findByText('Заказ получен')).toBeInTheDocument();
    expect(createShopRequest.mock.calls[1][0]).toEqual(createShopRequest.mock.calls[0][0]);
  });

  it('pri izmenenii kataloga ostavlyaet kontakty i trebuet proveryat novyj itog pered povtorom', async () => {
    createShopRequest.mockRejectedValueOnce(Object.assign(new Error(), { status: 409, code: 'CATALOG_CHANGED' }));
    fetchShopCatalog.mockResolvedValueOnce(catalog).mockResolvedValue({ ...catalog, version: 'next', offers: catalog.offers.map((offer) => ({ ...offer, priceMinor: offer.priceMinor + 10000 })) });
    show(); await addMini(); fillOrder();
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Каталог изменился'));
    expect(createShopRequest).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Ваше имя')).toHaveValue('Анна');
    expect(screen.getByLabelText('Адрес пункта СДЭК')).toHaveValue('ул. Садовая, 10');
    expect(screen.getByLabelText('Ваше имя')).not.toHaveAttribute('readonly');
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    await screen.findByText('Заказ получен');
    expect(createShopRequest.mock.calls[1][0].catalogVersion).toBe('next');
    expect(createShopRequest.mock.calls[1][0].idempotencyKey).not.toBe(createShopRequest.mock.calls[0][0].idempotencyKey);
  });

  it('konsultaciya ne trebuet pokupki i nikuda ne otpravlyaetsya do soglasiya', async () => {
    show({ cart: false });
    fireEvent.click(screen.getByRole('button', { name: 'Ask for help' }));
    const dialog = screen.getByRole('dialog');
    expect(createShopRequest).not.toHaveBeenCalled();
    expect(within(dialog).queryByLabelText('Код пункта СДЭК')).not.toBeInTheDocument();
    fillContacts();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Попросить помочь' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Попросить помочь' }));
    expect(createShopRequest).not.toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText('Согласен на обработку данных для связи и выполнения заявки.'));
    fireEvent.click(screen.getByRole('button', { name: 'Попросить помочь' }));
    expect(await screen.findByText('Заявка получена')).toBeInTheDocument();
    expect(createShopRequest.mock.calls[0][0]).toMatchObject({ kind: 'CONSULTATION', items: [], pickup: null });
  });

  it('ne obeshchaet priem zajavki pri nedostupnom kataloge', async () => {
    fetchShopCatalog.mockRejectedValueOnce(new Error());
    show(); fireEvent.click(screen.getByRole('button', { name: 'Add Mini' }));
    const retry = await screen.findByRole('button', { name: 'Проверить каталог' });
    expect(screen.getByRole('button', { name: 'Оформить заказ' })).toBeDisabled();
    fireEvent.click(retry);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Оформить заказ' })).toBeEnabled());
    expect(createShopRequest).not.toHaveBeenCalled();
  });

  it('posle HTTP413 sohranyaet vvod i razreshaet ispravit formu bez uncertain retry', async () => {
    createShopRequest.mockRejectedValueOnce(Object.assign(new Error(), { status: 413, code: 'REQUEST_FAILED' }));
    show(); await addMini(); fillOrder();
    fireEvent.click(screen.getByRole('button', { name: 'Оформить заказ' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Проверьте данные формы');
    expect(screen.getByLabelText('Ваше имя')).toHaveValue('Анна');
    expect(screen.getByLabelText('Ваше имя')).not.toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Оформить заказ' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Повторить проверку заказа' })).not.toBeInTheDocument();
  });

  it('anglijskaya forma lokalizovana i soderzhit ssylku na samostoyatelnyj poisk PVZ', async () => {
    await changeLocale('en', { remember: false });
    show({ path: '/en/cart/' }); fireEvent.click(screen.getByRole('button', { name: 'Add Mini' }));
    expect(screen.getByLabelText('CDEK pickup point code')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Find a pickup point on CDEK/u })).toHaveAttribute('href', 'https://www.cdek.ru/ru/offices/');
    expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
  });

  it('ne zagruzhaet magazin v rabochem kabinete', async () => {
    show({ path: '/app/', cart: false });
    await act(async () => {});
    expect(fetchShopCatalog).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
