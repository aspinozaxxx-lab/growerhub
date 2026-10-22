import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchResourceControls, releaseResourceControl, setResourceControl } from '../../api/automationControl';
import ResourceControls from './ResourceControls';

vi.mock('../../api/automationControl', () => ({ fetchResourceControls: vi.fn(), releaseResourceControl: vi.fn(), setResourceControl: vi.fn() }));
const resource = { id: 11, role: 'EXHAUST_SWITCH', label: 'Розетка вытяжки', current_value: 'OFF', ready: true };
const active = { resourceId: 11, status: 'active', sourceType: 'rule', sourceId: 'rule-1', sourceName: 'Обдув при свете', priority: 50, actualValue: 'ON', actualConfirmed: true, expiresAt: '2026-10-22T14:03:00Z', demands: [] };
let snapshot;
beforeEach(() => { snapshot = [active]; fetchResourceControls.mockImplementation(async () => snapshot); });
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const show = () => render(<ResourceControls resources={[resource]} refreshKey="farm" scopeName="Теплица" />);

describe('ResourceControls', () => {
  it('shows the actual owner and confirmation independently of the displayed switch state', async () => {
    show();
    expect(await screen.findByText('Обдув при свете')).toBeInTheDocument();
    expect(screen.getByText('Включено')).toBeInTheDocument();
    expect(screen.getByText('Устройство подтвердило состояние')).toBeInTheDocument();
    expect(screen.getByText(/До /)).toBeInTheDocument();
    expect(setResourceControl).not.toHaveBeenCalled();
  });
  it('requires an explicit duration and confirmation and waits for the server result', async () => {
    let finish;
    setResourceControl.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    show(); await screen.findByText('Обдув при свете');
    fireEvent.click(screen.getByRole('button', { name: 'Изменить на время' }));
    fireEvent.change(screen.getByLabelText('На сколько минут'), { target: { value: '10' } });
    expect(setResourceControl).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }));
    expect(setResourceControl).toHaveBeenCalledWith(11, { requestId: expect.any(String), value: 'OFF', ttlSeconds: 600 });
    expect(screen.getByText('Обдув при свете')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Подтвердить' })).toBeDisabled();
    snapshot = [{ ...active, status: 'pending', sourceName: 'Ручное управление', actualConfirmed: false }];
    finish(snapshot[0]);
    expect(await screen.findByText('Ручное управление')).toBeInTheDocument();
    expect(screen.getByText('Ждём подтверждения устройства')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Подтвердить' })).not.toBeInTheDocument());
  });
  it('reuses the same command id after an ambiguous network failure', async () => {
    setResourceControl.mockRejectedValue(new Error('Связь прервалась. Проверьте состояние.'));
    show(); await screen.findByText('Обдув при свете');
    fireEvent.click(screen.getByRole('button', { name: 'Изменить на время' }));
    fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }));
    await screen.findByRole('alert');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Подтвердить' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Подтвердить' }));
    await waitFor(() => expect(setResourceControl).toHaveBeenCalledTimes(2));
    expect(setResourceControl.mock.calls[1][1].requestId).toBe(setResourceControl.mock.calls[0][1].requestId);
  });
  it('releases only the exact manual demand revision, never another rule', async () => {
    const demand = { id: 'manual-one', revision: 7, sourceType: 'manual', sourceId: 'owner', sourceName: 'Ручное управление', desiredValue: 'OFF', priority: 200 };
    snapshot = [{ ...active, sourceType: 'manual', sourceId: 'owner', sourceName: 'Ручное управление', demands: [demand] }];
    releaseResourceControl.mockImplementation(async () => { snapshot = [active]; return active; });
    show(); await screen.findByText('Ручное управление');
    fireEvent.click(screen.getByRole('button', { name: 'Снять ручное управление' }));
    await screen.findByText('Обдув при свете');
    expect(releaseResourceControl).toHaveBeenCalledWith(11, demand);
    expect(setResourceControl).not.toHaveBeenCalled();
  });
});
