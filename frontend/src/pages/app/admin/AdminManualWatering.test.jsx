import React from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdminManualWatering from './AdminManualWatering';

const startWatering = vi.fn();
const clearActionError = vi.fn();
let actionError = '';
let capabilityOverrides = {};

vi.mock('../../../features/manual-watering/useAdminManualWatering', () => ({
  default: () => ({
    overview: {
      defaults: {
        timed_duration_s: 300,
        until_leak_max_active_duration_s: 1800,
        pulse_run_s: 180,
        pulse_pause_s: 300,
      },
      pumps: [{
        id: 7,
        label: 'Основной насос',
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
      }],
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

describe('AdminManualWatering', () => {
  afterEach(() => {
    cleanup();
    startWatering.mockReset();
    clearActionError.mockReset();
    actionError = '';
    capabilityOverrides = {};
  });

  it('pokazyvaet ierarhiyu i preobrazuet minutnye defaults v sekundy API', async () => {
    startWatering.mockResolvedValue(true);
    render(<AdminManualWatering />);

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
    render(<AdminManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(screen.getByLabelText('До протечки')).toBeDisabled();
    expect(screen.getByText('Режим доступен только при наличии рабочего датчика протечки.')).toBeInTheDocument();
  });

  it('pokazyvaet oshibku start vnutri otkrytogo modal', () => {
    actionError = 'Запуск запрещён сервером';
    render(<AdminManualWatering />);

    fireEvent.click(screen.getByRole('button', { name: 'Начать полив' }));
    expect(within(screen.getByRole('dialog')).getByRole('alert')).toHaveTextContent('Запуск запрещён сервером');
  });
});
