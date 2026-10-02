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

  it('klapan ne zapuskaetsya obshchim ON a zakrytie ostayotsya dostupnym', async () => {
    const valveOverview = structuredClone(overview);
    valveOverview.resource_catalog.zigbee_devices[0].watering = [{ property: 'state', ready: false,
      reason: 'Не подтверждено автономное закрытие' }];
    fetchFarmsOverview.mockResolvedValue(valveOverview);
    render(<MemoryRouter><AppZigbeeDevices embedded /></MemoryRouter>);
    await screen.findByText('Полив — через слот теплицы');
    expect(screen.queryByRole('button', { name: 'Включить' })).not.toBeInTheDocument();
    expect(screen.getByText('Не подтверждено автономное закрытие')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Выключить' }));
    await waitFor(() => expect(setZigbeeProperty).toHaveBeenCalledWith('coordinator-1', '0x01', 'state', 'OFF'));
    expect(setZigbeeProperty).toHaveBeenCalledTimes(1);
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

  it('shows cached values without claiming a fresh measurement', async () => {
    fetchFarmsOverview.mockResolvedValue({ ...overview, resource_catalog: { zigbee_devices: [
      { ...overview.resource_catalog.zigbee_devices[0], last_state_at: null, availability: null },
    ] } });
    render(<MemoryRouter><AppZigbeeDevices embedded /></MemoryRouter>);
    expect(await screen.findByText('Сохранённые показания. Ожидаем новое сообщение устройства.')).toBeVisible();
    const card = screen.getByRole('heading', { name: 'Датчик климата' }).closest('article');
    expect(card).toHaveTextContent('24');
    expect(card).not.toHaveTextContent('В сети');
  });

  it('filtruet kartochki po strochke i sostoyaniyu', async () => {
    render(
      <MemoryRouter>
        <AppZigbeeDevices />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: 'Датчик климата' });
    fireEvent.change(screen.getByPlaceholderText('Название, модель или ID'), {
      target: { value: 'ZBMINI' },
    });
    expect(screen.queryByRole('heading', { name: 'Датчик климата' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Реле света' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Состояние'), {
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
    const query = screen.getByPlaceholderText('Название, модель или ID');
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

  it('sohranyaet filtr teplicy pri obnovlenii i sbros ne otpravlyaet komandy', async () => {
    vi.useFakeTimers();
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    await act(async () => { render(<MemoryRouter><AppZigbeeDevices /></MemoryRouter>); });
    fireEvent.change(screen.getByLabelText('Размещение'), { target: { value: 'greenhouse:2' } });
    expect(screen.getByRole('heading', { name: 'Датчик климата' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Реле света' })).not.toBeInTheDocument();
    expect(screen.getByText('Показано 1 из 2')).toBeInTheDocument();
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getByLabelText('Размещение')).toHaveValue('greenhouse:2');
    fireEvent.change(screen.getByLabelText('Размещение'), { target: { value: 'unassigned' } });
    expect(screen.getByRole('heading', { name: 'Реле света' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Сбросить фильтры' }));
    expect(screen.getByRole('heading', { name: 'Датчик климата' })).toBeInTheDocument();
    expect(screen.getByLabelText('Размещение')).toHaveValue('all');
    expect(setZigbeeProperty).not.toHaveBeenCalled();
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
