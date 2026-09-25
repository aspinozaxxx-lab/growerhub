import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AppManualWatering from './AppManualWatering';

const startWatering = vi.fn();
const clearActionError = vi.fn();
const loadSessions = vi.fn();
let actionError = '';
let capabilityOverrides = {};
let demoActive = false;
let timedDurationS = 300;
let pumpIds = [7];
let histories = {};
let currentSession = null;
let resourceBindingId = null;

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ demoActive }),
}));

vi.mock('../../features/manual-watering/useManualWatering', () => ({
  default: () => ({
    overview: {
      defaults: {
        timed_duration_s: timedDurationS,
        until_leak_max_active_duration_s: 1800,
        pulse_run_s: 180,
        pulse_pause_s: 300,
      },
      pumps: pumpIds.map((id) => ({
        id,
        resource_binding_id: resourceBindingId,
        label: id === 7 ? 'Основной насос' : `Насос ${id}`,
        device_id: 'GH-1',
        channel: 1,
        is_online: true,
        is_running: false,
        capabilities: {
          can_start: true,
          can_stop: false,
          timed: true,
          until_leak: true,
          start_block_reasons: [],
          ...capabilityOverrides,
        },
        current_session: currentSession,
        boxes: [{
          id: 2,
          name: 'Бокс 2',
          room_name: 'Ферма',
          enabled: false,
          plants: [
            { id: 10, name: 'Томат', rate_ml_per_hour: 1200 },
            { id: 11, name: 'Базилик', rate_ml_per_hour: null },
          ],
          leak_sensors: [{ reference: 'leak-1', label: 'Дренаж', available: true, triggered: false }],
        }],
      })),
    },
    isLoading: false,
    error: '',
    actionError,
    notice: '',
    actionKey: '',
    histories,
    loadSessions,
    startWatering,
    stopWatering: vi.fn(),
    clearActionError,
  }),
}));

describe('AppManualWatering', () => {
  afterEach(() => {
    cleanup();
    startWatering.mockReset();
    clearActionError.mockReset();
    loadSessions.mockReset();
    actionError = '';
    capabilityOverrides = {};
    demoActive = false;
    timedDurationS = 300;
    pumpIds = [7];
    histories = {};
    currentSession = null;
    resourceBindingId = null;
  });

  it('zapuskaet klapan po privyazke i ne predlagaet impulsy', async () => {
    pumpIds = [null];
    resourceBindingId = 42;
    capabilityOverrides = { pulse: false, until_leak: false, max_duration_s: 600 };
    startWatering.mockResolvedValue(true);
    render(<AppManualWatering />);
    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.queryByRole('checkbox', { name: 'Импульсный режим' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));
    await waitFor(() => expect(startWatering).toHaveBeenCalledWith('resource:42', {
      mode: 'timed', duration_s: 300, pulse_enabled: false,
    }));
  });

  it('otkryvaet zhurnal tolko zapushchennogo nasosa bez ozhidaniya zagruzki istorii', async () => {
    pumpIds = [7, 8];
    startWatering.mockResolvedValue(true);
    loadSessions.mockReturnValue(new Promise(() => {}));
    render(<AppManualWatering />);
    const selectedPump = screen.getByRole('heading', { name: 'Насос 8 · канал 1' }).closest('.manual-watering-pump');
    const otherPump = screen.getByRole('heading', { name: 'Основной насос · канал 1' }).closest('.manual-watering-pump');

    fireEvent.click(within(selectedPump).getByRole('button', { name: 'Начать полив' }));
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(within(selectedPump).getByRole('button', { name: 'Скрыть журнал' })).toHaveAttribute('aria-expanded', 'true');
    expect(within(otherPump).getByRole('button', { name: 'Журнал насоса' })).toHaveAttribute('aria-expanded', 'false');
    expect(loadSessions).toHaveBeenCalledExactlyOnceWith(8);
  });

  it('ne otkryvaet zhurnal pri otklonennom zapuske', async () => {
    startWatering.mockResolvedValue(false);
    render(<AppManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));

    await waitFor(() => expect(startWatering).toHaveBeenCalledOnce());
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Журнал насоса' })).toHaveAttribute('aria-expanded', 'false');
    expect(loadSessions).not.toHaveBeenCalled();
  });

  it('povtorno otkryvaet zagruzhennyj zhurnal bez lishnego zaprosa', async () => {
    startWatering.mockResolvedValue(true);
    histories = { 7: { loaded: true, items: [] } };
    render(<AppManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Скрыть журнал' })).toHaveAttribute('aria-expanded', 'true');
    expect(loadSessions).not.toHaveBeenCalled();
  });

  it('obnovlyaet tekushchuyu zapis zhurnala iz overview i ne zatiraet zavershennyj rezultat', () => {
    const initialSession = {
      id: 10, source: 'user_manual', mode: 'timed', phase: 'running',
      started_at: '2026-09-18T10:00:00Z', finished_at: null,
      active_duration_s: 0, known_volume_l: 0,
    };
    histories = { 7: { loaded: true, items: [initialSession] } };
    currentSession = { ...initialSession, active_duration_s: 30, known_volume_l: 0.4 };
    const { rerender } = render(<AppManualWatering />);
    fireEvent.click(screen.getByRole('button', { name: 'Журнал насоса' }));
    const journal = () => within(screen.getByRole('region', { name: 'Журнал: Основной насос · канал 1' }));
    expect(journal().getByText('30 сек')).toBeInTheDocument();
    expect(journal().getByText('0,4 л')).toBeInTheDocument();

    currentSession = { ...currentSession, active_duration_s: 45, known_volume_l: 0.6 };
    rerender(<AppManualWatering />);
    expect(journal().getByText('45 сек')).toBeInTheDocument();
    expect(journal().getByText('0,6 л')).toBeInTheDocument();

    histories = { 7: { loaded: true, items: [{
      ...initialSession, active_duration_s: 60, known_volume_l: 0.8,
      finished_at: '2026-09-18T10:01:00Z', completion_reason: 'duration',
    }] } };
    rerender(<AppManualWatering />);
    expect(journal().getByText('1 мин')).toBeInTheDocument();
    expect(journal().getByText('0,8 л')).toBeInTheDocument();
    expect(journal().getByText('Завершён по времени')).toBeInTheDocument();
  });

  it('pokazyvaet ierarhiyu i preobrazuet minutnye defaults v sekundy API', async () => {
    startWatering.mockResolvedValue(true);
    render(<AppManualWatering />);

    expect(screen.getByText('Бокс 2')).toBeInTheDocument();
    expect(screen.getByText('Томат')).toBeInTheDocument();
    expect(screen.getByText('Скорость не указана')).toBeInTheDocument();
    expect(screen.getByText('Выключен в автоматизации')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.getByLabelText('Общее активное время, мин')).toHaveValue(5);

    fireEvent.click(screen.getByLabelText('До протечки'));
    expect(screen.getByLabelText('Предельное активное время, мин')).toHaveValue(30);
    fireEvent.click(screen.getByLabelText('Импульсный режим'));
    expect(screen.getByLabelText('Работа насоса, мин')).toHaveValue(3);
    expect(screen.getByLabelText('Пауза, мин')).toHaveValue(5);
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));

    await waitFor(() => expect(startWatering).toHaveBeenCalledWith(7, {
      mode: 'until_leak',
      max_active_duration_s: 1800,
      pulse_enabled: true,
      pulse_run_s: 180,
      pulse_pause_s: 300,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    fireEvent.click(screen.getByLabelText('До протечки'));
    fireEvent.change(screen.getByLabelText('Предельное активное время, мин'), { target: { value: '' } });
    fireEvent.click(screen.getByLabelText('По времени'));
    fireEvent.click(screen.getByLabelText('Импульсный режим'));
    fireEvent.change(screen.getByLabelText('Работа насоса, мин'), { target: { value: '' } });
    fireEvent.click(screen.getByLabelText('Импульсный режим'));
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));

    await waitFor(() => expect(startWatering).toHaveBeenNthCalledWith(2, 7, {
      mode: 'timed',
      duration_s: 300,
      pulse_enabled: false,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('blokiruet until leak po servernoj capability', () => {
    capabilityOverrides = { until_leak: false };
    render(<AppManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.getByLabelText('До протечки')).toBeDisabled();
    expect(screen.getByText('Режим доступен только при наличии рабочего датчика протечки.')).toBeInTheDocument();
  });

  it.each([
    [false, 420, 7],
    [true, 300, 1],
  ])('ispolzuet nachalnye minuty dlya demo=%s i sohranyaet ruchnoj vybor', async (isDemo, serverSeconds, initialMinutes) => {
    demoActive = isDemo;
    timedDurationS = serverSeconds;
    startWatering.mockResolvedValue(true);
    render(<AppManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.getByLabelText('Общее активное время, мин')).toHaveValue(initialMinutes);
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));
    await waitFor(() => expect(startWatering).toHaveBeenNthCalledWith(1, 7, {
      mode: 'timed', duration_s: initialMinutes * 60, pulse_enabled: false,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    fireEvent.change(screen.getByLabelText('Общее активное время, мин'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));
    await waitFor(() => expect(startWatering).toHaveBeenNthCalledWith(2, 7, {
      mode: 'timed', duration_s: 120, pulse_enabled: false,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.getByLabelText('Общее активное время, мин')).toHaveValue(initialMinutes);
  });

  it('pokazyvaet oshibku start vnutri otkrytogo modal', () => {
    actionError = 'Запуск запрещён сервером';
    render(<AppManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(within(screen.getByRole('dialog')).getByRole('alert')).toHaveTextContent('Запуск запрещён сервером');
  });

  it('sohranyaet poryadok nasosov i vybor formy pri novom poryadke overview', async () => {
    pumpIds = [25, 22, 23, 24];
    startWatering.mockResolvedValue(true);
    const { rerender } = render(<AppManualWatering />);
    const pumpOrder = () => screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent);
    const expectedOrder = [22, 23, 24, 25].map((id) => `Насос ${id} · канал 1`);

    expect(pumpOrder()).toEqual(expectedOrder);
    const chosenPump = screen.getByRole('heading', { name: 'Насос 25 · канал 1' }).closest('.manual-watering-pump');
    fireEvent.click(within(chosenPump).getByRole('button', { name: 'Начать полив' }));

    pumpIds = [22, 23, 24, 25];
    rerender(<AppManualWatering />);
    expect(pumpOrder()).toEqual(expectedOrder);
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Запуск: Насос 25 · канал 1');
    fireEvent.click(screen.getByRole('button', { name: 'Запустить' }));
    await waitFor(() => expect(startWatering).toHaveBeenCalledWith(25, {
      mode: 'timed', duration_s: 300, pulse_enabled: false,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(pumpOrder()).toEqual(expectedOrder);
  });
});
