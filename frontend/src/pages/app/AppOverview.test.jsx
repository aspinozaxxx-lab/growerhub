import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  fetchFarmsOverview,
  fetchManualWateringGreenhouseStatistics,
} from '../../api/selfService';
import AppOverview from './AppOverview';

const { openSensorStats } = vi.hoisted(() => ({ openSensorStats: vi.fn() }));

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  fetchManualWateringGreenhouseStatistics: vi.fn(),
  stopManualWatering: vi.fn(),
}));

vi.mock('../../features/sensors/SensorStatsContext', () => ({
  useSensorStatsContext: () => ({ openSensorStats }),
}));

describe('AppOverview', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('pokazyvaet v teplicah tolko privyazannye resursy bez rasteniy i aktivnogo statusa', async () => {
    fetchFarmsOverview.mockResolvedValue({
      farms: [{
        id: 1,
        name: 'Ферма',
        enabled: true,
        slots: [{
          id: 10,
          role: 'AC_SWITCH',
          ready: true,
          current_value: 'OFF',
        }],
        scenarios: [],
        states: [],
        greenhouses: [{
          id: 2,
          name: 'Активная теплица',
          enabled: true,
          plants: [{ id: 30, name: 'Базилик' }],
          slots: [
            {
              id: 11,
              role: 'AC_SWITCH',
              ready: true,
              current_value: 'ON',
            },
            {
              id: 12,
              role: 'EXHAUST_SWITCH',
              ready: true,
              current_value: 'OFF',
            },
            {
              id: 13,
              role: 'AIR_TEMPERATURE_SENSOR',
              ready: true,
              current_value: 24.5,
            },
          ],
          scenarios: [],
          readiness: {},
          states: [{
            scenario_type: 'BOX_CLIMATE',
            runtime: { ac_control: { phase: 'handling_request' } },
          }],
          last_actions: [],
        }, {
          id: 3,
          name: 'Выключенная теплица',
          enabled: false,
          plants: [],
          slots: [],
          scenarios: [],
          readiness: {},
          states: [],
          last_actions: [],
        }],
        last_actions: [],
      }],
      resource_catalog: {
        plants: [
          { id: 30, name: 'Базилик' },
          { id: 31, name: 'Томат' },
        ],
        native_devices: [],
        zigbee_devices: [],
      },
    });

    render(
      <MemoryRouter>
        <AppOverview />
      </MemoryRouter>,
    );

    const activeHeading = await screen.findByRole('heading', { name: 'Активная теплица' });
    const activeGreenhouse = activeHeading.closest('.farm-dashboard-box');
    const farmCard = activeHeading.closest('.farm-dashboard-room');
    const equipment = activeGreenhouse.querySelector('.farm-dashboard-box__equipment');

    expect(within(equipment).getByText('Обдув')).toBeInTheDocument();
    expect(within(equipment).queryByText('Кондиционер')).not.toBeInTheDocument();
    expect(within(equipment).queryByText('Свет')).not.toBeInTheDocument();
    expect(within(equipment).queryByText('Полив')).not.toBeInTheDocument();
    expect(within(activeGreenhouse).queryByText('Свет')).not.toBeInTheDocument();
    expect(within(activeGreenhouse).queryByText('Полив')).not.toBeInTheDocument();
    expect(within(activeGreenhouse).getByText('Температура воздуха')).toBeInTheDocument();
    expect(within(activeGreenhouse).queryByText('Не привязано')).not.toBeInTheDocument();
    expect(within(activeGreenhouse).queryByText('Активен')).not.toBeInTheDocument();
    expect(within(activeGreenhouse).queryByText(/Кондиционер обрабатывает/u)).not.toBeInTheDocument();
    expect(screen.queryByText('Базилик')).not.toBeInTheDocument();
    expect(within(farmCard).queryByText('Растения')).not.toBeInTheDocument();

    const disabledHeading = screen.getByRole('heading', { name: 'Выключенная теплица' });
    const disabledGreenhouse = disabledHeading.closest('.farm-dashboard-box');
    expect(within(disabledGreenhouse).getByText('Выключен')).toBeInTheDocument();
    expect(disabledGreenhouse.querySelector('.farm-dashboard-box__equipment')).toBeNull();
    expect(disabledGreenhouse.querySelector('.farm-dashboard-sensors')).toBeNull();
    expect(within(disabledGreenhouse).queryByText('Обдув')).not.toBeInTheDocument();
    expect(within(disabledGreenhouse).queryByText('Полив')).not.toBeInTheDocument();
  });

  it('sohranyaet statistiku datchikov i oborudovaniya v oblasti polzovatelya', async () => {
    fetchFarmsOverview.mockResolvedValue({
      farms: [{
        id: 1, name: 'Ферма', enabled: true, greenhouses: [{
          id: 2, name: 'Северная', enabled: true,
          slots: [{
            id: 10, role: 'AIR_TEMPERATURE_SENSOR', source_type: 'NATIVE_SENSOR',
            native_sensor_id: 51, ready: true, current_value: 24.6,
          }, {
            id: 11, role: 'LIGHT_SWITCH', source_type: 'ZIGBEE_DEVICE',
            zigbee_ieee_address: 'test-device', ready: true, current_value: 'ON',
          }],
        }],
      }],
    });
    render(<MemoryRouter><AppOverview /></MemoryRouter>);

    fireEvent.click(await screen.findByRole('button', { name: 'Открыть статистику: Температура воздуха' }));
    expect(openSensorStats).toHaveBeenLastCalledWith(expect.objectContaining({
      mode: 'sensor', sensorId: 51, metric: 'air_temperature', subtitle: 'Северная',
      zigbeeHistoryScope: 'self-service', equipmentStatsScope: 'self-service',
    }));
    fireEvent.click(screen.getByRole('button', { name: 'Открыть статистику: Свет' }));
    expect(openSensorStats).toHaveBeenLastCalledWith(expect.objectContaining({
      mode: 'equipment', equipmentResourceId: 11, subtitle: 'Северная',
      equipmentStatsScope: 'self-service',
    }));
  });

  it('otkryvaet ruchnoy poliv i zhurnal nasosa iz plashki poliva', async () => {
    fetchFarmsOverview.mockResolvedValue({
      farms: [{
        id: 1,
        name: 'Ферма',
        enabled: true,
        slots: [],
        scenarios: [],
        states: [],
        greenhouses: [{
          id: 2,
          name: 'Северная',
          enabled: true,
          plants: [],
          slots: [{
            id: 12,
            role: 'WATER_PUMP',
            source_type: 'NATIVE_PUMP',
            native_pump_id: 51,
            ready: true,
            current_value: 'OFF',
          }],
          scenarios: [],
          readiness: {},
          states: [],
          last_actions: [],
        }],
        last_actions: [],
      }],
      resource_catalog: {
        plants: [],
        native_devices: [],
        zigbee_devices: [],
      },
    });
    fetchManualWateringGreenhouseStatistics.mockResolvedValue({
      session_count: 0,
      active_duration_s: 0,
      known_volume_l: 0,
      mode_counts: {},
      reason_counts: {},
      sessions: [],
      active_session: null,
      next_before_id: null,
    });

    render(
      <MemoryRouter>
        <AppOverview />
      </MemoryRouter>,
    );

    const manualWateringLink = await screen.findByRole('link', { name: 'Ручной полив' });
    expect(manualWateringLink).toHaveAttribute('href', '/app/manual-watering/');

    fireEvent.click(screen.getByRole('button', { name: 'Открыть статистику: Полив' }));

    expect(await screen.findByText('Журнал насоса')).toBeInTheDocument();
    const pumpJournal = screen.getByRole('dialog', { name: 'Журнал насоса' });
    expect(within(pumpJournal).getByText('Северная')).toBeInTheDocument();
    expect(fetchManualWateringGreenhouseStatistics).toHaveBeenCalledWith(2, {
      range: 'day',
      limit: 10,
      beforeId: null,
    });
  });
});
