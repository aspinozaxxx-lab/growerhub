import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AppManualWatering from './AppManualWatering';

const startWatering = vi.fn();
const clearActionError = vi.fn();
let actionError = '';
let capabilityOverrides = {};
let demoActive = false;
let timedDurationS = 300;
let pumpIds = [7];

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
        current_session: null,
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
    histories: {},
    loadSessions: vi.fn(),
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
    actionError = '';
    capabilityOverrides = {};
    demoActive = false;
    timedDurationS = 300;
    pumpIds = [7];
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
