import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchAdminShopRequest, fetchAdminShopRequests, retryShopNotification, updateAdminShopRequest } from '../../api/shop';
import { changeLocale } from '../../locales/i18n';
import AdminRequestsPanel from './AdminRequestsPanel';

vi.mock('../../api/shop', () => ({ fetchAdminShopRequest: vi.fn(), fetchAdminShopRequests: vi.fn(), retryShopNotification: vi.fn(), updateAdminShopRequest: vi.fn() }));

const entry = { id: 1, number: 'GH-12', kind: 'ORDER', status: 'NEW', createdAt: '2026-10-05T12:00:00', totalMinor: 329000, currency: 'RUB', customer: { name: 'Анна', phone: '+79991234567', telegram: '@anna' }, pickup: { city: 'Тверь', code: 'TVR1', address: 'Садовая, 10' }, comment: 'Позвоните после 14:00', items: [{ offerId: 'light-mini', quantity: 1, unitPriceMinor: 329000, hubModel: 'POK101', socketCount: 1, verification: 'PILOT' }], notification: { status: 'uncertain', attempts: 1 } };
const show = (path = '/app/admin/shop/requests/') => render(<MemoryRouter initialEntries={[path]}><AdminRequestsPanel /></MemoryRouter>);

describe('Admin shop requests', () => {
  beforeEach(async () => {
    vi.resetAllMocks(); await changeLocale('ru', { remember: false });
    fetchAdminShopRequests.mockResolvedValue({ requests: [entry], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    updateAdminShopRequest.mockResolvedValue({ ...entry, status: 'PROCESSING' });
  });
  afterEach(cleanup);

  it('raskryvaet zajavku iz Telegram i ne predlagaet slepoj povtor uncertain-uvedomleniya', async () => {
    show('/app/admin/shop/requests/?request=1');
    await screen.findByText('GH-12');
    expect(screen.getByText('GH-12').closest('details')).toHaveAttribute('open');
    expect(screen.getByText('GH-12').closest('.gh-shop-admin')).toHaveClass('ym-hide-content');
    expect(screen.getByRole('button', { name: 'Повторить уведомление' })).toBeDisabled();
    expect(retryShopNotification).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Статус заявки'), { target: { value: 'PROCESSING' } });
    expect(updateAdminShopRequest).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить статус' }));
    await waitFor(() => expect(updateAdminShopRequest).toHaveBeenCalledWith(1, 'PROCESSING'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Сохранить статус' })).toBeDisabled());
  });

  it('povtoryaet uncertain tolko posle yavnogo podtverzhdeniya proverki chata', async () => {
    retryShopNotification.mockResolvedValue({ ...entry, notification: { status: 'queued', attempts: 1 } });
    show('/app/admin/shop/requests/?request=1'); await screen.findByText('GH-12');
    const retry = screen.getByRole('button', { name: 'Повторить уведомление' });
    fireEvent.click(retry);
    expect(retryShopNotification).not.toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText('Я проверил чат: этого уведомления там нет'));
    fireEvent.click(retry);
    await waitFor(() => expect(retryShopNotification).toHaveBeenCalledExactlyOnceWith(1));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Повторить уведомление' })).not.toBeInTheDocument());
  });

  it('dostupaetsya po pryamoj ssylke k zajavke ne s pervoj stranicy', async () => {
    fetchAdminShopRequests.mockResolvedValue({ requests: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    fetchAdminShopRequest.mockResolvedValue(entry);
    show('/app/admin/shop/requests/?request=1');
    expect(await screen.findByText('GH-12')).toBeInTheDocument();
    expect(fetchAdminShopRequest).toHaveBeenCalledWith('1');
    expect(screen.getByText('GH-12').closest('details')).toHaveAttribute('open');
  });

  it('oshibka PATCH ne podmenyaet sohranennyj status', async () => {
    updateAdminShopRequest.mockRejectedValue(new Error());
    show('/app/admin/shop/requests/?request=1'); await screen.findByText('GH-12');
    fireEvent.change(screen.getByLabelText('Статус заявки'), { target: { value: 'CONFIRMED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить статус' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Предыдущее состояние сохранено');
    expect(document.querySelector('.gh-shop-admin__badge')).toHaveTextContent('Новая');
  });
});
