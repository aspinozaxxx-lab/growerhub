import React from 'react';
import {
  cleanup,
  render,
  screen,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchFarmsOverview,
  replaceGreenhouseScenarios,
} from '../../api/selfService';
import AppAutomations from './AppAutomations';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  replaceGreenhouseScenarios: vi.fn(),
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
});
