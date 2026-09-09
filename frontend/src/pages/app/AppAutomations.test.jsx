import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchFarmsOverview,
  replaceGreenhouseScenarios,
  setScenariosEnabled,
} from '../../api/selfService';
import AppAutomations from './AppAutomations';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  replaceGreenhouseScenarios: vi.fn(),
  setScenariosEnabled: vi.fn(),
}));

const scenarioConfig = {
  max_c: 28,
  exhaust_off_below_c: 27,
  ac_request_above_c: 29,
  ac_clear_below_c: 27,
};

function overview(withAirConditioners = true) {
  return {
    farms: [{
      id: 1,
      name: 'Ферма',
      enabled: true,
      slots: withAirConditioners ? [{ id: 10, role: 'AC_SWITCH' }] : [],
      scenarios: [{
        scenario_type: 'ROOM_CLIMATE',
        enabled: true,
        config: {},
      }],
      greenhouses: [{
        id: 2,
        name: 'Теплица',
        enabled: true,
        slots: withAirConditioners ? [{ id: 11, role: 'AC_SWITCH' }] : [],
        scenarios: [{
          scenario_type: 'BOX_CLIMATE',
          enabled: true,
          config: scenarioConfig,
          readiness: { ready: true },
        }],
        readiness: { BOX_CLIMATE: { ready: true } },
      }],
    }],
  };
}

describe('AppAutomations', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it('pokazyvaet tolko obshchie chetyre polya klimata', async () => {
    fetchFarmsOverview.mockResolvedValue(overview(true));
    render(
      <MemoryRouter>
        <AppAutomations />
      </MemoryRouter>,
    );

    expect(await screen.findAllByLabelText('Обдув включить выше, °C')).toHaveLength(1);
    expect(screen.getAllByLabelText('Обдув выключить ниже, °C')).toHaveLength(1);
    expect(screen.getAllByLabelText('Запрос охлаждения выше, °C')).toHaveLength(1);
    expect(screen.getAllByLabelText('Снять запрос ниже, °C')).toHaveLength(1);
    expect(screen.queryByLabelText('Минимум, °C')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Задержка выключения, мин')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Защита от частых переключений, мин')).not.toBeInTheDocument();
    expect(screen.queryByText('Общий кондиционер фермы')).not.toBeInTheDocument();
    expect(screen.queryByText('Настройки кондиционера')).not.toBeInTheDocument();
    expect(replaceGreenhouseScenarios).not.toHaveBeenCalled();
  });

  it('skryvaet nastrojki kondicionera bez sootvetstvuyushchego slota', async () => {
    fetchFarmsOverview.mockResolvedValue(overview(false));
    render(
      <MemoryRouter>
        <AppAutomations />
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText('Обдув включить выше, °C')).toBeInTheDocument();
    expect(screen.queryByLabelText('Задержка выключения, мин')).not.toBeInTheDocument();
    expect(screen.queryByText('Настройки кондиционера')).not.toBeInTheDocument();
    expect(screen.queryByText('Общий кондиционер фермы')).not.toBeInTheDocument();
  });

  it('sohranyaet chernoviki drugih teplic i ne otpravlyaet sosednie scenarii', async () => {
    const payload = multiOverview();
    fetchFarmsOverview.mockResolvedValue(payload);
    const saved = structuredClone(payload);
    saved.farms[0].greenhouses[1].scenarios[1].config.end_time = '19:15';
    replaceGreenhouseScenarios.mockResolvedValue(saved);
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByLabelText('Начало');
    fireEvent.change(screen.getByLabelText('Начало'), { target: { value: '23:15' } });
    fireEvent.click(screen.getByRole('button', { name: /Зелень/ }));
    expect(screen.getByLabelText('Начало')).toHaveValue('08:00');
    fireEvent.change(screen.getByLabelText('Конец'), { target: { value: '19:15' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    await waitFor(() => expect(replaceGreenhouseScenarios).toHaveBeenCalledWith(3, [{
      scenario_type: 'LIGHT_SCHEDULE', enabled: true,
      config: { start_time: '08:00', end_time: '19:15', custom: 'kept' },
    }]));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Сохранить', exact: true })).toBeDisabled());
    fireEvent.click(screen.getByRole('button', { name: /Рассада/ }));
    expect(screen.getByLabelText('Начало')).toHaveValue('23:15');
    expect(screen.getByText('Через полночь')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Сохранить', exact: true })).toBeEnabled();
  });

  it('obshchij vykljuchatel ne sbrasyvaet chernoviki i ispolzuet odin zapros', async () => {
    const payload = multiOverview();
    fetchFarmsOverview.mockResolvedValue(payload);
    const off = structuredClone(payload);
    off.farms[0].scenarios.forEach((scenario) => { scenario.enabled = false; });
    off.farms[0].greenhouses.forEach((greenhouse) => greenhouse.scenarios.forEach((scenario) => { scenario.enabled = false; }));
    setScenariosEnabled.mockResolvedValue(off);
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByLabelText('Начало');
    fireEvent.change(screen.getByLabelText('Начало'), { target: { value: '05:40' } });
    fireEvent.click(screen.getByRole('button', { name: 'Выключить всё' }));
    await screen.findByRole('button', { name: 'Включить всё' });
    expect(setScenariosEnabled).toHaveBeenCalledExactlyOnceWith(false);
    expect(replaceGreenhouseScenarios).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Начало')).toHaveValue('05:40');
    expect(screen.getByRole('switch', { name: 'Освещение: Рассада' })).toHaveAttribute('aria-checked', 'false');
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    fireEvent.click(screen.getByRole('button', { name: 'Включить всё' }));
    expect(confirm).toHaveBeenCalledOnce();
    expect(setScenariosEnabled).toHaveBeenCalledTimes(1);
  });

  it('sohranyaet znacheniya i sostoyanie pri oshibke API', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview());
    replaceGreenhouseScenarios.mockRejectedValue(new Error('Ошибка сохранения'));
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByLabelText('Начало');
    fireEvent.change(screen.getByLabelText('Конец'), { target: { value: '04:20' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить', exact: true }));
    await screen.findByText('Ошибка сохранения');
    expect(screen.getByLabelText('Конец')).toHaveValue('04:20');
    expect(screen.getByRole('switch', { name: 'Освещение: Рассада' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('button', { name: 'Сохранить', exact: true })).toBeEnabled();
  });

  it('vkljuchaet ostalnye scenarii bez predvaritelnogo obshchego otkljucheniya', async () => {
    const payload = multiOverview();
    payload.farms[0].greenhouses[0].scenarios[1].enabled = false;
    fetchFarmsOverview.mockResolvedValue(payload);
    setScenariosEnabled.mockResolvedValue(multiOverview());
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByRole('button', { name: 'Включить всё' });
    expect(screen.getByRole('button', { name: 'Выключить всё' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Включить всё' }));
    await waitFor(() => expect(setScenariosEnabled).toHaveBeenCalledExactlyOnceWith(true));
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Освещение: Рассада' })).toHaveAttribute('aria-checked', 'true'));
  });

  it('nedostupnyj scenarij mozhno nastroit no nelzya vkljuchit', async () => {
    const payload = multiOverview();
    const greenhouse = payload.farms[0].greenhouses[0];
    greenhouse.readiness.LIGHT_SCHEDULE = { ready: false, reason: 'Нет выключателя' };
    greenhouse.scenarios[1].enabled = false;
    fetchFarmsOverview.mockResolvedValue(payload);
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    expect(await screen.findByRole('switch', { name: 'Освещение: Рассада' })).toBeDisabled();
    expect(screen.getByRole('slider', { name: 'Начало освещения: Рассада' })).toBeEnabled();
  });
});

function multiOverview() {
  const payload = overview();
  payload.farms[0].greenhouses = ['Рассада', 'Зелень'].map((name, index) => ({
    id: index + 2, name, enabled: true, slots: [],
    scenarios: [
      { scenario_type: 'BOX_CLIMATE', enabled: true, config: scenarioConfig },
      { scenario_type: 'LIGHT_SCHEDULE', enabled: true, config: { start_time: index ? '08:00' : '06:00', end_time: '22:00', custom: 'kept' } },
      { scenario_type: 'WATERING', enabled: true, config: { soil_threshold_percent: 35, run_seconds: 30, daily_max_seconds: 300 } },
    ],
    readiness: { BOX_CLIMATE: { ready: true }, LIGHT_SCHEDULE: { ready: true }, WATERING: { ready: true } },
  }));
  return payload;
}
