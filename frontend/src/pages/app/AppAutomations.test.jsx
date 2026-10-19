import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ruleRequest } from '../../api/assistant';
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
vi.mock('../../api/assistant', () => ({ ruleRequest: vi.fn() }));
let personalRules;

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
  beforeEach(() => {
    personalRules = [];
    ruleRequest.mockImplementation(async (path = '') => {
      if (!path) return personalRules;
      if (path.endsWith('/history') || path.endsWith('/versions')) return [];
      if (path.startsWith('/preview')) return { conflicts: [], readiness: [], timing: null };
      return {};
    });
  });
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('keeps all personal rules at the bottom independently of the selected greenhouse', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview());
    personalRules = [personalRule('watering-3', 'Полив через день', 'BOX', 3), personalRule('seedling-check', 'Осмотр рассады', 'BOX', 2), personalRule('farm-check', 'Обход фермы', 'ROOM', 1)];
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByRole('button', { name: 'Открыть сценарий «Обход фермы»' });
    expect(screen.getByRole('button', { name: 'Открыть сценарий «Полив через день»' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Открыть сценарий «Осмотр рассады»' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Настройки теплицы'), { target: { value: '3' } });
    expect(screen.getByRole('button', { name: 'Открыть сценарий «Осмотр рассады»' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Свои сценарии: Рассада' })).toBeInTheDocument();
    const group = screen.getByRole('region', { name: 'Свои сценарии: Зелень' });
    expect(within(group).getByRole('button', { name: 'Открыть сценарий «Полив через день»' })).toBeInTheDocument();
    expect(within(group).getByRole('switch', { name: 'Сценарий: Полив через день' })).not.toBeChecked();
    expect(screen.queryByText('Пауза между запусками')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Автоматизации фермы: Ферма' })).toBeInTheDocument();
    expect(ruleRequest.mock.calls.filter(([path]) => !path)).toHaveLength(1);
    expect(ruleRequest.mock.calls.some(([path]) => path?.includes('/enabled'))).toBe(false);
  });

  it('opens an owned rule link in its greenhouse without enabling anything', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview());
    personalRules = [personalRule('watering-3', 'Полив через день', 'BOX', 3)];
    render(<MemoryRouter initialEntries={['/app/automations/?rule=watering-3']}><AppAutomations /></MemoryRouter>);
    const dialog = await screen.findByRole('dialog', { name: 'Полив через день' });
    await within(dialog).findByText('Устройства готовы, конфликтов нет');
    expect(screen.getByLabelText('Настройки теплицы')).toHaveValue('3');
    expect(within(dialog).getByText('Зелень')).toBeInTheDocument();
    expect(ruleRequest.mock.calls.some(([path]) => path?.includes('/enabled'))).toBe(false);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Закрыть' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('does not request details for an unknown or foreign rule identifier in the URL', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview());
    render(<MemoryRouter initialEntries={['/app/automations/?rule=foreign']}><AppAutomations /></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Сценарий не найден в текущей ферме');
    expect(ruleRequest.mock.calls.every(([path]) => !path)).toBe(true);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('switches a personal routine on and off in the list using its current revision', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview()); personalRules = [personalRule('custom', 'Осмотр', 'BOX', 2)];
    ruleRequest.mockImplementation(async (path = '', method, body) => {
      if (!path) return [...personalRules];
      if (path === '/custom/enabled' && method === 'PUT') { personalRules = personalRules.map(r => ({ ...r, enabled: body.enabled, revision: r.revision + 1 })); return personalRules[0]; }
      return {};
    });
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    const toggle = await screen.findByRole('switch', { name: 'Сценарий: Осмотр' });
    expect(toggle).not.toBeChecked(); fireEvent.click(toggle);
    await waitFor(() => expect(toggle).toBeChecked());
    expect(ruleRequest).toHaveBeenCalledWith('/custom/enabled', 'PUT', { enabled: true, revision: 1 });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(toggle); await waitFor(() => expect(toggle).not.toBeChecked());
    expect(ruleRequest).toHaveBeenLastCalledWith();
    expect(ruleRequest).toHaveBeenCalledWith('/custom/enabled', 'PUT', { enabled: false, revision: 2 });
    expect(ruleRequest.mock.calls.filter(([path]) => path?.includes('/history') || path?.startsWith('/preview'))).toHaveLength(0);
  });
  it('keeps a rejected switch off and shows the server conflict beside that routine', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview()); personalRules = [personalRule('custom', 'Полив с паузами', 'BOX', 2)];
    const previous = ruleRequest.getMockImplementation();
    ruleRequest.mockImplementation((path, ...rest) => path === '/custom/enabled' ? Promise.reject(new Error('Канал занят поливом')) : previous(path, ...rest));
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    const toggle = await screen.findByRole('switch', { name: 'Сценарий: Полив с паузами' }); fireEvent.click(toggle);
    expect(await within(screen.getByRole('region', { name: 'Свои сценарии: Рассада' })).findByRole('alert')).toHaveTextContent('Канал занят поливом');
    expect(toggle).not.toBeChecked(); expect(toggle).toBeEnabled(); expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('refreshes actual state after a concurrent change without sending a second command', async () => {
    fetchFarmsOverview.mockResolvedValue(multiOverview()); personalRules = [personalRule('custom', 'Осмотр', 'BOX', 2)];
    ruleRequest.mockImplementation(async (path = '') => {
      if (!path) return [...personalRules];
      if (path === '/custom/enabled') { personalRules = personalRules.map(r => ({ ...r, enabled: true, revision: 2 })); throw new Error('Версия сценария изменилась'); }
      return {};
    });
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    const toggle = await screen.findByRole('switch', { name: 'Сценарий: Осмотр' }); fireEvent.click(toggle);
    await waitFor(() => expect(toggle).toBeChecked());
    expect(await screen.findByRole('alert')).toHaveTextContent('Версия сценария изменилась');
    expect(ruleRequest.mock.calls.filter(([path]) => path === '/custom/enabled')).toHaveLength(1);
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

  it('mobilnye paneli sohranyayut chernoviki pri zakrytii i vybere drugoj teplicy', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    const payload = multiOverview();
    const saved = structuredClone(payload);
    saved.farms[0].greenhouses[1].scenarios[0].config.max_c = 31;
    fetchFarmsOverview.mockResolvedValue(payload);
    replaceGreenhouseScenarios.mockResolvedValue(saved);
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    await screen.findByRole('button', { name: 'Настроить климат: Рассада' });
    expect(screen.queryByLabelText('Обдув включить выше, °C')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Настроить климат: Рассада' }));
    let dialog = screen.getByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Обдув включить выше, °C'), { target: { value: '30' } });
    fireEvent.keyDown(within(dialog).getByRole('button', { name: 'Закрыть' }), { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(replaceGreenhouseScenarios).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /Зелень 08:00/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Настроить климат: Зелень' }));
    dialog = screen.getByRole('dialog');
    expect(within(dialog).getByLabelText('Обдув включить выше, °C')).toHaveValue(28);
    fireEvent.change(within(dialog).getByLabelText('Обдув включить выше, °C'), { target: { value: '31' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить климат' }));
    await waitFor(() => expect(replaceGreenhouseScenarios).toHaveBeenCalledExactlyOnceWith(3, [{
      scenario_type: 'BOX_CLIMATE', enabled: true, config: { ...scenarioConfig, max_c: 31 },
    }]));
    await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Сохранить климат' })).toBeDisabled());
    fireEvent.click(within(dialog).getByRole('button', { name: 'Закрыть' }));
    fireEvent.click(screen.getByRole('button', { name: /Рассада 06:00/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Настроить климат: Рассада' }));
    expect(within(screen.getByRole('dialog')).getByLabelText('Обдув включить выше, °C')).toHaveValue(30);
  });

  it('mobilnaya shkala otkryvaet tochnye polya i pokazyvaet oshibku v paneli', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
    fetchFarmsOverview.mockResolvedValue(multiOverview());
    replaceGreenhouseScenarios.mockRejectedValue(new Error('Ошибка сохранения'));
    render(<MemoryRouter><AppAutomations /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: 'Расписание освещения: Рассада' }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('slider', { name: 'Начало освещения: Рассада' })).toBeEnabled();
    fireEvent.change(within(dialog).getByLabelText('Начало'), { target: { value: '23:30' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить', exact: true }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Ошибка сохранения');
    expect(within(dialog).getByLabelText('Начало')).toHaveValue('23:30');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Закрыть' }));
    fireEvent.click(screen.getByRole('button', { name: /Все сценарии/ }));
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Выключить всё' })).toBeEnabled();
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

function personalRule(id, name, scopeType, scopeId) {
  return { id, enabled: false, revision: 1, definition: { version: 2, name, scopeType, scopeId,
    trigger: { type: 'calendar', every: 2, unit: 'day', startDate: '2026-10-20', at: '16:00' },
    actions: [{ type: 'notify', text: 'Осмотр растений' }], cooldownSeconds: 60 } };
}
