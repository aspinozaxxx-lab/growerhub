import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { connectPushok, fetchPushokAvailability, fetchPushokConnection, retryPushokPairing } from '../api/pushokConnection';
import { changeLocale, loadAppTranslations } from '../locales/i18n';
import PushokConnect from './PushokConnect';

vi.mock('../api/pushokConnection', () => ({ connectPushok: vi.fn(), fetchPushokAvailability: vi.fn(), fetchPushokConnection: vi.fn(), retryPushokPairing: vi.fn() }));
const pending = { id: 'test', hub_id: 'pushok-A1B2C3-1234', name: 'ПушОк', connection_status: 'PAIRING' };
const show = (props = {}, path = '/') => render(<MemoryRouter initialEntries={[path]}><PushokConnect {...props} /></MemoryRouter>);
describe('PushokConnect', () => {
  beforeEach(async () => {
    vi.resetAllMocks(); await loadAppTranslations(); await changeLocale('ru', { remember: false });
    fetchPushokAvailability.mockResolvedValue({ available: true });
    connectPushok.mockResolvedValue(pending);
    fetchPushokConnection.mockResolvedValue({ ...pending, connection_status: 'ACTIVE' });
  });
  afterEach(cleanup);
  it('ne zaprashivaet LAN i ne sozdaet dostup bez yavnogo podtverzhdeniya', async () => {
    show({}, '/app/start/?connect=pushok');
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    const button = screen.getByRole('button', { name: 'Кнопка нажата — подключить' });
    expect(button).toBeDisabled(); expect(connectPushok).not.toHaveBeenCalled();
    expect(screen.queryByLabelText(/IP|Пароль Wi-Fi/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('ID шлюза из Управлятора'), { target: { value: 'pushok-A1B2C3-1234' } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(button);
    await waitFor(() => expect(connectPushok).toHaveBeenCalledExactlyOnceWith({ name: 'ПушОк', hub_id: 'pushok-A1B2C3-1234', confirm_access: true }));
    expect(await screen.findByText(/Подключаемся к шлюзу через облако/)).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'ПушОк подключён' }, { timeout: 4000 })).toBeInTheDocument();
  });
  it('ne vydajot otkaz soprjazheniya za uspeshnoe podklyuchenie', async () => {
    const failed = { ...pending, connection_status: 'ERROR', connection_error: 'PAIRING_REQUIRED' };
    retryPushokPairing.mockResolvedValue(pending);
    show({ coordinator: failed });
    const button = await screen.findByRole('button', { name: 'Повторить привязку ПушОк' });
    await waitFor(() => expect(button).not.toBeDisabled());
    fireEvent.click(button);
    expect(screen.getByRole('alert')).toHaveTextContent('ПушОк не разрешил подключение');
    expect(screen.getByLabelText('ID шлюза из Управлятора')).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox')); fireEvent.click(screen.getByRole('button', { name: 'Кнопка нажата — подключить' }));
    await waitFor(() => expect(retryPushokPairing).toHaveBeenCalledExactlyOnceWith('test'));
    expect(connectPushok).not.toHaveBeenCalled();
  });
  it('posle zakrytiya vozobnovlyaet tekuschuyu privyazku bez novogo dostupa', async () => {
    fetchPushokConnection.mockResolvedValue(pending);
    show({}, '/app/start/?connect=pushok');
    await screen.findByRole('dialog');
    fireEvent.change(screen.getByLabelText('ID шлюза из Управлятора'), { target: { value: pending.hub_id } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: 'Кнопка нажата — подключить' }));
    await screen.findByText(/Подключаемся к шлюзу через облако/);
    fireEvent.click(screen.getByRole('button', { name: 'Закрыть' }));
    fireEvent.click(screen.getByRole('button', { name: /^(Подключить|Продолжить подключение) ПушОк$/u }));
    expect(screen.getByText(/Подключаемся к шлюзу через облако/)).toBeVisible();
    expect(screen.queryByLabelText('ID шлюза из Управлятора')).not.toBeInTheDocument();
    expect(connectPushok).toHaveBeenCalledTimes(1);
    expect(retryPushokPairing).not.toHaveBeenCalled();
    fetchPushokConnection.mockResolvedValue({ ...pending, connection_status: 'ACTIVE' });
    await screen.findByRole('heading', { name: 'ПушОк подключён' }, { timeout: 4000 });
    expect(connectPushok).toHaveBeenCalledTimes(1);
  });
});
