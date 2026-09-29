import React from 'react';
import {
  cleanup,
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchFarmsOverview, setZigbeeProperty } from '../../api/selfService';
import AppZigbeeDevices from './AppZigbeeDevices';

vi.mock('../../api/selfService', () => ({
  fetchFarmsOverview: vi.fn(),
  setZigbeeProperty: vi.fn(),
}));

const overview = {
  farms: [{
    id: 1,
    name: 'Основная ферма',
    slots: [],
    greenhouses: [{
      id: 2,
      name: 'Теплица 2',
      slots: [{
        role: 'AIR_TEMPERATURE_SENSOR',
        source_type: 'ZIGBEE_DEVICE',
        zigbee_coordinator_id: 'coordinator-1',
        zigbee_ieee_address: '0x01',
        zigbee_property: 'temperature',
      }],
    }],
  }],
  resource_catalog: {
    zigbee_devices: [
      {
        coordinator_id: 'coordinator-1',
        coordinator_name: 'Основной',
        ieee_address: '0x01',
        friendly_name: 'Датчик климата',
        availability: 'online',
        last_state_at: '2026-07-29T12:00:00',
        definition: { vendor: 'Aqara', model: 'WSDCGQ11LM' },
        metrics: [
          { property: 'temperature', label: 'Температура', value: 24, unit: '°C' },
          { property: 'humidity', label: 'Влажность', value: 55, unit: '%' },
          { property: 'battery', label: 'Батарея', value: 91, unit: '%' },
          { property: 'power', label: 'Мощность', value: 3, unit: 'W' },
          { property: 'linkquality', label: 'Связь', value: 80 },
          { property: 'voltage', label: 'Напряжение', value: 230, unit: 'V' },
          { property: 'energy', label: 'Энергия', value: 12, unit: 'kWh' },
        ],
        controls: [{
          type: 'binary',
          property: 'state',
          label: 'Питание',
          value: 'OFF',
          value_on: 'ON',
          value_off: 'OFF',
        }],
      },
      {
        coordinator_id: 'coordinator-1',
        coordinator_name: 'Основной',
        ieee_address: '0x02',
        friendly_name: 'Реле света',
        availability: 'offline',
        definition: { vendor: 'Sonoff', model: 'ZBMINI' },
        metrics: [],
        controls: [],
      },
    ],
  },
};

describe('AppZigbeeDevices', () => {
  beforeEach(() => {
    fetchFarmsOverview.mockResolvedValue(overview);
    setZigbeeProperty.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('pokazyvaet kompaktnuyu kartochku, roli i upravlenie state', async () => {
    render(
      <MemoryRouter>
        <AppZigbeeDevices embedded />
      </MemoryRouter>,
    );

    const title = await screen.findByRole('heading', { name: 'Датчик климата' });
    expect(screen.getByRole('heading', { name: 'Zigbee-устройства' })).toBeInTheDocument();
    const card = title.closest('article');
    expect(card).toHaveClass('farm-device-card');
    expect(card).toHaveTextContent('Основная ферма · Теплица 2 · Температура воздуха');
    expect(card).toHaveTextContent('Подробнее');
    expect(card.querySelectorAll('.farm-device-card__metrics > div')).toHaveLength(6);

    fireEvent.click(screen.getByRole('button', { name: 'Включить' }));
    await waitFor(() => {
      expect(setZigbeeProperty).toHaveBeenCalledWith(
        'coordinator-1',
        '0x01',
        'state',
        'ON',
      );
    });
  });

  it('filtruet kartochki po strochke i sostoyaniyu', async () => {
    render(
      <MemoryRouter>
        <AppZigbeeDevices />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: 'Датчик климата' });
    fireEvent.change(screen.getByPlaceholderText('Название, модель или IEEE-адрес'), {
      target: { value: 'ZBMINI' },
    });
    expect(screen.queryByRole('heading', { name: 'Датчик климата' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Реле света' })).toBeInTheDocument();

    fireEvent.change(screen.getByDisplayValue('Все устройства'), {
      target: { value: 'online' },
    });
    expect(screen.getByText('По заданным условиям устройств нет')).toBeInTheDocument();
  });

  it('poluchaet pozdnee podtverzhdenie bez povtora komandy i sbrosa poiska', async () => {
    vi.useFakeTimers();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    let view;
    await act(async () => { view = render(<MemoryRouter><AppZigbeeDevices /></MemoryRouter>); });
    const card = screen.getByRole('heading', { name: 'Датчик климата' }).closest('article');
    const query = screen.getByPlaceholderText('Название, модель или IEEE-адрес');
    fireEvent.change(query, { target: { value: 'Aqara' } });
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Включить' })); });
    expect(card.querySelector('.farm-device-card__state > strong')).toHaveTextContent('OFF');

    const confirmed = structuredClone(overview);
    confirmed.resource_catalog.zigbee_devices[0].controls[0].value = 'ON';
    fetchFarmsOverview.mockResolvedValue(confirmed);
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(card.querySelector('.farm-device-card__state > strong')).toHaveTextContent('ON');
    expect(query).toHaveValue('Aqara');
    expect(setZigbeeProperty).toHaveBeenCalledTimes(1);

    view.unmount();
    const requests = fetchFarmsOverview.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(fetchFarmsOverview).toHaveBeenCalledTimes(requests);
  });

  it('fonovoe chtenie ne skryvaet oshibku komandy i ne povtoryaet ee', async () => {
    vi.useFakeTimers();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    await act(async () => { render(<MemoryRouter><AppZigbeeDevices /></MemoryRouter>); });
    setZigbeeProperty.mockRejectedValue(new Error('Нет связи с координатором'));
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Включить' })); });
    expect(screen.getByText('Нет связи с координатором')).toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getByText('Нет связи с координатором')).toBeInTheDocument();
    expect(setZigbeeProperty).toHaveBeenCalledTimes(1);
  });
});
