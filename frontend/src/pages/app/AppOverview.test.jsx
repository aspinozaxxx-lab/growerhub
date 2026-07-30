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
import { fetchFarmsOverview } from '../../api/selfService';
import AppOverview from './AppOverview';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
}));

vi.mock('../../features/sensors/SensorStatsContext', () => ({
  useSensorStatsContext: () => ({ openSensorStats: vi.fn() }),
}));

describe('AppOverview warnings', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('pokazyvaet v tooltip vse preduprezhdeniya i tot zhe schetchik', async () => {
    fetchFarmsOverview.mockResolvedValue({
      farms: [{
        id: 1,
        name: 'Ферма',
        enabled: true,
        slots: [{
          id: 10,
          role: 'AC_SWITCH',
          ready: true,
          connection_status: 'warning',
          connection_message: 'нет связи',
        }],
        scenarios: [],
        states: [{ id: 20, scenario_type: 'ROOM_CLIMATE', ac_request_active: true }],
        greenhouses: [{
          id: 2,
          name: 'Теплица',
          enabled: true,
          plants: [],
          slots: [{
            id: 11,
            role: 'AIR_TEMPERATURE_SENSOR',
            ready: false,
            reason: 'Устройство Zigbee не найдено',
          }],
          scenarios: [],
          readiness: {
            LIGHT_SCHEDULE: { ready: false, reason: 'Нужен Zigbee-выключатель света' },
          },
          states: [{ id: 21, scenario_type: 'BOX_CLIMATE', ac_request_active: true }],
          last_actions: [],
        }],
        last_actions: [],
      }],
      resource_catalog: {
        plants: [{ id: 3, name: 'Томат' }],
        native_devices: [],
        zigbee_devices: [],
      },
    });

    render(
      <MemoryRouter>
        <AppOverview />
      </MemoryRouter>,
    );

    const label = await screen.findByText('Предупреждения');
    const tile = label.closest('article');
    const tooltip = within(tile).getByRole('tooltip');
    expect(tile).toHaveAttribute('tabindex', '0');
    expect(tile).toHaveAttribute('aria-describedby', tooltip.id);
    expect(within(tile).getByText('6')).toBeInTheDocument();
    expect(within(tooltip).getAllByRole('listitem')).toHaveLength(6);
    expect(within(tooltip).getByText('Кондиционер — нет связи')).toBeInTheDocument();
    expect(within(tooltip).getByText('Размещение — Не выбрана теплица')).toBeInTheDocument();
    fireEvent.click(tile);
    expect(tile).toHaveAttribute('aria-expanded', 'true');
    expect(tile).toHaveClass('is-tooltip-open');
    fireEvent.keyDown(tile, { key: 'Escape' });
    expect(tile).toHaveAttribute('aria-expanded', 'false');
  });
});
