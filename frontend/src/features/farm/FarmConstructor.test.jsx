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
import {
  fetchFarmsOverview,
  replaceGreenhouseScenarios,
  replaceGreenhouseSlots,
  replaceUserFarmSlots,
  updateGreenhousePlantWateringRate,
} from '../../api/selfService';
import { fetchPlant, updatePlant } from '../../api/plants';
import FarmConstructor from './FarmConstructor';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  replaceGreenhouseScenarios: vi.fn(),
  replaceGreenhouseSlots: vi.fn(),
  replaceUserFarmSlots: vi.fn(),
  updateGreenhousePlantWateringRate: vi.fn(),
}));

vi.mock('../../api/plants', () => ({
  fetchPlant: vi.fn(),
  updatePlant: vi.fn(),
  createPlant: vi.fn(),
  deletePlant: vi.fn(),
}));

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ token: 'test-token' }),
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
    }, {
      id: 3,
      name: 'Южная',
      enabled: true,
      plants: [],
      slots: [],
      scenarios: [],
      states: [],
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
    const plantName = within(greenhouse).getByRole('button', { name: 'Томат' });
    expect(plantName).toBeInTheDocument();
    expect(within(plantName.closest('.farm-zone-plant')).getAllByRole('button')).toHaveLength(1);
    expect(within(greenhouse).queryByText('В этой теплице')).not.toBeInTheDocument();
    expect(within(greenhouse).queryByRole('checkbox', { name: 'Томат' })).not.toBeInTheDocument();
    expect(within(greenhouse).queryByLabelText('Температура воздуха')).not.toBeInTheDocument();
    expect(screen.queryByText('Слот свободен')).not.toBeInTheDocument();
    expect(screen.queryByText('ТЕПЛИЦА')).not.toBeInTheDocument();
    expect(screen.queryByText('Разные свойства комбинированного датчика можно назначать отдельно.'))
      .not.toBeInTheDocument();
    expect(screen.queryByText('Backend проверяет готовность по назначенным слотам до включения.'))
      .not.toBeInTheDocument();
    expect(within(greenhouse).getByLabelText('Обдув включить выше, °C')).toBeInTheDocument();
    expect(within(greenhouse).getByLabelText('Обдув выключить ниже, °C')).toBeInTheDocument();
    expect(within(greenhouse).getByLabelText('Запрос охлаждения выше, °C')).toBeInTheDocument();
    expect(within(greenhouse).getByLabelText('Снять запрос ниже, °C')).toBeInTheDocument();
    expect(within(greenhouse).queryByLabelText('Минимум, °C')).not.toBeInTheDocument();
    expect(within(greenhouse).queryByLabelText('Задержка выключения, мин')).not.toBeInTheDocument();
    expect(within(greenhouse).queryByLabelText('Защита от частых переключений, мин'))
      .not.toBeInTheDocument();
    expect(within(greenhouse).queryByText('Настройки кондиционера')).not.toBeInTheDocument();

    fireEvent.change(within(greenhouse).getByLabelText('Тип нового слота'), {
      target: { value: 'AIR_TEMPERATURE_SENSOR' },
    });
    fireEvent.click(within(greenhouse).getByRole('button', { name: 'Добавить слот' }));

    expect(within(greenhouse).getByLabelText('Температура воздуха')).toBeInTheDocument();
    expect(replaceUserFarmSlots).not.toHaveBeenCalled();
    expect(replaceGreenhouseSlots).not.toHaveBeenCalled();
    expect(updateGreenhousePlantWateringRate).not.toHaveBeenCalled();
    expect(replaceGreenhouseScenarios).not.toHaveBeenCalled();
  });

  it('pokazyvaet aktualnyj status svjazi dlya svezhego binding', async () => {
    fetchFarmsOverview.mockResolvedValueOnce({
      ...overview,
      farms: [{
        ...overview.farms[0],
        greenhouses: [{
          ...overview.farms[0].greenhouses[0],
          slots: [{
            id: 99,
            role: 'AIR_TEMPERATURE_SENSOR',
            source_type: 'ZIGBEE_DEVICE',
            zigbee_coordinator_id: '7b17f42e-28ad-48a0-9f61-f9f3fe160a85',
            zigbee_ieee_address: '0xabc',
            zigbee_property: 'temperature',
            current_value: 23.4,
            connection_status: 'ok',
            connection_message: null,
            label: 'Датчик',
          }],
        }],
      }],
    });

    render(
      <MemoryRouter>
        <FarmConstructor />
      </MemoryRouter>,
    );

    const greenhouse = (await screen.findByRole('heading', { name: 'Северная' })).closest('article');
    expect(within(greenhouse).getByText((_, element) => (
      element.classList.contains('farm-slot__status')
      && element.textContent.includes('на связи')
    ))).toBeInTheDocument();
    expect(within(greenhouse).queryByText('статус неизвестен')).not.toBeInTheDocument();
  });

  it('sohranyaet tolko skorost poliva i otkryvaet obshchij dialog rastenija', async () => {
    updateGreenhousePlantWateringRate.mockResolvedValue(overview);
    fetchPlant.mockResolvedValue({
      id: 5,
      name: 'Томат',
      plant_type: 'tomato',
      zone: { id: 2, name: 'Северная' },
    });
    updatePlant.mockResolvedValue({});

    render(
      <MemoryRouter>
        <FarmConstructor />
      </MemoryRouter>,
    );

    const greenhouse = (await screen.findByRole('heading', { name: 'Северная' })).closest('article');
    const rate = within(greenhouse).getByLabelText('Скорость полива для Томат, мл/ч');
    fireEvent.change(rate, { target: { value: '140' } });
    fireEvent.blur(rate);

    await waitFor(() => expect(updateGreenhousePlantWateringRate).toHaveBeenCalledWith(2, 5, 140));
    expect(await within(greenhouse).findByText('Сохранено')).toBeInTheDocument();

    fireEvent.click(within(greenhouse).getByRole('button', { name: 'Томат' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(fetchPlant).toHaveBeenCalledWith(null, 5);
  });

  it('vosstanavlivaet servernuyu skorost pri oshibke', async () => {
    updateGreenhousePlantWateringRate.mockRejectedValue(new Error('Сервер недоступен'));

    render(
      <MemoryRouter>
        <FarmConstructor />
      </MemoryRouter>,
    );

    const greenhouse = (await screen.findByRole('heading', { name: 'Северная' })).closest('article');
    const rate = within(greenhouse).getByLabelText('Скорость полива для Томат, мл/ч');
    fireEvent.change(rate, { target: { value: '150' } });
    fireEvent.blur(rate);

    await waitFor(() => expect(updateGreenhousePlantWateringRate).toHaveBeenCalledWith(2, 5, 150));
    await waitFor(() => expect(rate).toHaveValue(120));
    expect(within(greenhouse).getByText('Не сохранено')).toBeInTheDocument();
  });

  it('srazu perenosit kartochku posle sohraneniya obshchego dialoga', async () => {
    const movedOverview = {
      ...overview,
      farms: [{
        ...overview.farms[0],
        greenhouses: overview.farms[0].greenhouses.map((greenhouse) => (
          greenhouse.id === 2
            ? { ...greenhouse, plants: [] }
            : { ...greenhouse, plants: [{ id: 5, name: 'Томат', rate_ml_per_hour: 120 }] }
        )),
      }],
    };
    fetchFarmsOverview
      .mockResolvedValueOnce(overview)
      .mockResolvedValueOnce(movedOverview);
    fetchPlant.mockResolvedValue({
      id: 5,
      name: 'Томат',
      plant_type: 'tomato',
      zone: { id: 2, name: 'Северная' },
    });
    updatePlant.mockResolvedValue({});

    render(
      <MemoryRouter>
        <FarmConstructor />
      </MemoryRouter>,
    );

    const north = (await screen.findByRole('heading', { name: 'Северная' })).closest('article');
    fireEvent.click(within(north).getByRole('button', { name: 'Томат' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Теплица'), { target: { value: '3' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => expect(updatePlant).toHaveBeenCalledWith(
      'test-token',
      5,
      expect.objectContaining({ zone_id: 3 }),
    ));
    await waitFor(() => {
      const south = screen.getByRole('heading', { name: 'Южная' }).closest('article');
      expect(within(south).getByRole('button', { name: 'Томат' })).toBeInTheDocument();
      expect(within(north).queryByRole('button', { name: 'Томат' })).not.toBeInTheDocument();
    });
  });
});
