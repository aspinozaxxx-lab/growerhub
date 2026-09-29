import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchPushokPilot, savePushokPilot, withdrawPushokPilot } from '../api/pushokPilot';
import { changeLocale, loadAppTranslations } from '../locales/i18n';
import PushokPilot from './PushokPilot';

vi.mock('../api/pushokPilot', () => ({ fetchPushokPilot: vi.fn(), savePushokPilot: vi.fn(), withdrawPushokPilot: vi.fn() }));

const saved = { contact_method: 'TELEGRAM', contact: '@pilot_user', equipment: 'POK100', requested_at: '2026-09-27T09:00:00' };
const show = (path = '/app/settings/connections/', support = false) => render(<MemoryRouter initialEntries={[path]}><PushokPilot support={support} /></MemoryRouter>);

describe('PushokPilot', () => {
  beforeEach(async () => {
    vi.resetAllMocks();
    await loadAppTranslations();
    await changeLocale('ru', { remember: false });
    fetchPushokPilot.mockResolvedValue({ request: null });
    savePushokPilot.mockResolvedValue({ request: saved });
    withdrawPushokPilot.mockResolvedValue({});
  });
  afterEach(() => cleanup());

  it('prosmotr ne sozdaet zajavku', async () => {
    show();
    expect(fetchPushokPilot).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Подключить ПушОк' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Просто смотрю' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(savePushokPilot).not.toHaveBeenCalled();
  });

  it('otpravlyaet kontakt tolko posle yavnogo vybora i pokazivaet podtverzhdenie servera', async () => {
    show('/app/settings/connections/?pilot=pushok');
    fireEvent.click(await screen.findByRole('button', { name: 'Хочу участвовать' }));
    expect(savePushokPilot).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Отправить заявку' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Контакт для ответа'), { target: { value: '@pilot_user' } });
    fireEvent.change(screen.getByLabelText('Какое оборудование есть (необязательно)'), { target: { value: 'POK100' } });
    fireEvent.click(screen.getByRole('button', { name: 'Отправить заявку' }));
    expect(await screen.findByText('Заявка сохранена')).toBeInTheDocument();
    expect(savePushokPilot).toHaveBeenCalledExactlyOnceWith({ contact_method: 'TELEGRAM', contact: '@pilot_user', equipment: 'POK100' });
  });

  it.each([false, true])('vosstanavlivaet zajavku i pozvolyaet otozvat kontakt, support=%s', async (support) => {
    fetchPushokPilot.mockResolvedValue({ request: saved });
    show('/app/settings/connections/?pilot=pushok', support);
    if (support) {
      expect(fetchPushokPilot).not.toHaveBeenCalled();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Помощь с ПушОк и моя заявка' }));
    }
    expect(await screen.findByText('Telegram: @pilot_user')).toHaveClass('ym-hide-content');
    fireEvent.click(screen.getByRole('button', { name: 'Отозвать заявку' }));
    expect(await screen.findByText('Заявка отозвана, контакт удалён из списка пилота.')).toBeInTheDocument();
    expect(withdrawPushokPilot).toHaveBeenCalledTimes(1);
    expect(savePushokPilot).not.toHaveBeenCalled();
  });

  it('oshibka ne vydaetsya za uspeshnuyu otpravku i ne teryaet vvod', async () => {
    savePushokPilot.mockRejectedValue(new Error('Unavailable'));
    show('/app/settings/connections/?pilot=pushok');
    fireEvent.click(await screen.findByRole('button', { name: 'Хочу участвовать' }));
    fireEvent.change(screen.getByLabelText('Контакт для ответа'), { target: { value: '@pilot_user' } });
    fireEvent.click(screen.getByRole('button', { name: 'Отправить заявку' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText('Заявка сохранена')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Контакт для ответа')).toHaveValue('@pilot_user');
  });

  it('oshibka chteniya ne razreshaet nezametno perezapisat sushchestvuyushchuyu zajavku', async () => {
    fetchPushokPilot.mockRejectedValueOnce(new Error('Unavailable'));
    show('/app/settings/connections/?pilot=pushok');
    fireEvent.click(await screen.findByRole('button', { name: 'Повторить' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Хочу участвовать' })).toBeInTheDocument());
    expect(fetchPushokPilot).toHaveBeenCalledTimes(2);
    expect(savePushokPilot).not.toHaveBeenCalled();
  });

  it('ne pokazyvaet uspeh bez podtverzhdennoj serverom zajavki', async () => {
    savePushokPilot.mockResolvedValue({});
    show('/app/settings/connections/?pilot=pushok');
    fireEvent.click(await screen.findByRole('button', { name: 'Хочу участвовать' }));
    expect(screen.getByLabelText('Как с вами связаться')).toHaveFocus();
    fireEvent.change(screen.getByLabelText('Контакт для ответа'), { target: { value: '@pilot_user' } });
    fireEvent.click(screen.getByRole('button', { name: 'Отправить заявку' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText('Заявка сохранена')).not.toBeInTheDocument();
  });

  it('anglijskij pilot ne soderzhit neperevedennyh polej', async () => {
    await changeLocale('en', { remember: false });
    show('/app/settings/connections/?pilot=pushok');
    fireEvent.click(await screen.findByRole('button', { name: 'I would like to take part' }));
    expect(document.body.textContent).not.toMatch(/[\u0400-\u04ff]/u);
  });
});
