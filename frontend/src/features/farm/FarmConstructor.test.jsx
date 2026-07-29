import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  fetchFarmsOverview,
  replaceGreenhousePlants,
  replaceGreenhouseScenarios,
  replaceGreenhouseSlots,
  replaceUserFarmSlots,
} from '../../api/selfService';
import FarmConstructor from './FarmConstructor';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  replaceGreenhousePlants: vi.fn(),
  replaceGreenhouseScenarios: vi.fn(),
  replaceGreenhouseSlots: vi.fn(),
  replaceUserFarmSlots: vi.fn(),
}));

const overview = {
  farms: [{
    id: 1,
    name: 'Основная ферма',
    enabled: true,
    slots: [],
    scenarios: [],
    states: [{
      scenario_type: 'ROOM_CLIMATE',
      ac_request_active: true,
      runtime: { pending_request_count: 1 },
    }],
    greenhouses: [{
      id: 2,
      name: 'Северная',
      enabled: true,
      plants: [{ id: 5, name: 'Томат', rate_ml_per_hour: 120 }],
      slots: [],
      scenarios: [],
      states: [{
        scenario_type: 'BOX_CLIMATE',
        ac_request_active: true,
      }],
      readiness: {},
    }],
  }],
  resource_catalog: {
    plants: [{ id: 5, name: 'Томат' }],
    native_devices: [],
    zigbee_devices: [],
  },
};

describe('FarmConstructor', () => {
  beforeEach(() => {
    fetchFarmsOverview.mockResolvedValue(overview);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('dobavlyaet sloty vyborochno i pokazyvaet zapros na ohlazhdenie bez kondicionera', async () => {
    render(
      <MemoryRouter>
        <FarmConstructor />
      </MemoryRouter>,
    );

    const greenhouseTitle = await screen.findByRole('heading', { name: 'Северная' });
    const greenhouse = greenhouseTitle.closest('article');

    expect(within(greenhouse).getByText('Требуется охлаждение')).toBeInTheDocument();
    expect(within(greenhouse).getByText('В этой теплице')).toBeInTheDocument();
    expect(within(greenhouse).queryByLabelText('Температура воздуха')).not.toBeInTheDocument();
    expect(screen.queryByText('Слот свободен')).not.toBeInTheDocument();
    expect(screen.queryByText('ТЕПЛИЦА')).not.toBeInTheDocument();
    expect(screen.queryByText('Разные свойства комбинированного датчика можно назначать отдельно.'))
      .not.toBeInTheDocument();
    expect(screen.queryByText('Backend проверяет готовность по назначенным слотам до включения.'))
      .not.toBeInTheDocument();

    fireEvent.change(within(greenhouse).getByLabelText('Тип нового слота'), {
      target: { value: 'AIR_TEMPERATURE_SENSOR' },
    });
    fireEvent.click(within(greenhouse).getByRole('button', { name: 'Добавить слот' }));

    expect(within(greenhouse).getByLabelText('Температура воздуха')).toBeInTheDocument();
    expect(replaceUserFarmSlots).not.toHaveBeenCalled();
    expect(replaceGreenhouseSlots).not.toHaveBeenCalled();
    expect(replaceGreenhousePlants).not.toHaveBeenCalled();
    expect(replaceGreenhouseScenarios).not.toHaveBeenCalled();
  });
});
