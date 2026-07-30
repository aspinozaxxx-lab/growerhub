import { describe, expect, it } from 'vitest';
import {
  assignmentsForZigbeeDevice,
  buildSlotOccupancy,
  createScenarioDrafts,
  countFarmWarnings,
  filterZigbeeDevices,
  farmOverviewToDashboardRooms,
  findUnassignedFarmPlants,
  findSlotConflicts,
  listFarmWarnings,
  priorityDeviceMetrics,
} from './farmModel';
import { optionValue } from './farmResourceOptions';

const coordinatorId = '4f69fd28-bad8-4bd1-b2ff-6cc61ec15f50';

describe('farm model', () => {
  it('schitaet raznye svojstva kombinirovannogo datchika raznymi kanalami', () => {
    const zones = [{
      id: 1,
      name: 'Теплица 1',
      slots: [{
        role: 'AIR_TEMPERATURE_SENSOR',
        source_type: 'ZIGBEE_DEVICE',
        zigbee_coordinator_id: coordinatorId,
        zigbee_ieee_address: '0x1234',
        zigbee_property: 'temperature',
      }],
    }];
    const humidity = optionValue({
      source_type: 'ZIGBEE_DEVICE',
      zigbee_coordinator_id: coordinatorId,
      zigbee_ieee_address: '0x1234',
      zigbee_property: 'humidity',
    });
    const temperature = optionValue({
      source_type: 'ZIGBEE_DEVICE',
      zigbee_coordinator_id: coordinatorId,
      zigbee_ieee_address: '0x1234',
      zigbee_property: 'temperature',
    });

    expect(buildSlotOccupancy(zones).size).toBe(1);
    expect(findSlotConflicts(2, { AIR_HUMIDITY_SENSOR: humidity }, zones)).toEqual([]);
    expect(findSlotConflicts(2, { AIR_TEMPERATURE_SENSOR: temperature }, zones))
      .toMatchObject([{ zoneId: 1, zoneName: 'Теплица 1' }]);
  });

  it('stroitr proekciyu dashboard iz ferm i teplic', () => {
    const rooms = farmOverviewToDashboardRooms({
      farms: [{
        id: 7,
        name: 'Ферма',
        enabled: true,
        slots: [{ role: 'AC_SWITCH' }],
        scenarios: [{ scenario_type: 'ROOM_CLIMATE', enabled: true }],
        states: [],
        greenhouses: [{
          id: 8,
          name: 'Теплица',
          enabled: true,
          plants: [{ id: 3, name: 'Томат' }],
          slots: [{ role: 'LIGHT_SWITCH' }],
          scenarios: [
            { scenario_type: 'BOX_CLIMATE', enabled: true },
            { scenario_type: 'LIGHT_SCHEDULE' },
          ],
          states: [],
          readiness: {},
          last_actions: [],
        }],
      }],
    });

    expect(rooms[0].id).toBe(7);
    expect(rooms[0].boxes[0]).toMatchObject({
      id: 8,
      name: 'Теплица',
      plants: [{ id: 3, name: 'Томат' }],
    });
    expect(rooms[0].scenarios).toEqual([{
      scenario_type: 'ROOM_CLIMATE',
      enabled: true,
    }]);
  });

  it('pokazyvaet vse rasteniya i schitaet nerazmeschennye preduprezhdeniyami', () => {
    const overview = {
      farms: [{
        greenhouses: [{
          id: 7,
          plants: [{ id: 3, name: 'Томат' }],
          slots: [],
          readiness: {},
        }],
      }],
      resource_catalog: {
        plants: [
          { id: 3, name: 'Томат' },
          { id: 4, name: 'Розмарин' },
        ],
      },
    };

    expect(findUnassignedFarmPlants(overview)).toEqual([{ id: 4, name: 'Розмарин' }]);
    expect(countFarmWarnings(overview)).toBe(1);
  });

  it('vozvrashchaet detalnyj spisok dlya kazhdogo preduprezhdeniya', () => {
    const overview = {
      farms: [{
        id: 1,
        name: 'Ферма',
        slots: [{
          id: 10,
          role: 'AC_SWITCH',
          ready: true,
          connection_status: 'warning',
          connection_message: 'нет связи',
        }],
        states: [{ id: 20, scenario_type: 'ROOM_CLIMATE', ac_request_active: true }],
        greenhouses: [{
          id: 2,
          name: 'Теплица',
          plants: [],
          slots: [{
            id: 11,
            role: 'AIR_TEMPERATURE_SENSOR',
            ready: false,
            reason: 'Устройство Zigbee не найдено',
          }],
          readiness: {
            LIGHT_SCHEDULE: { ready: false, reason: 'Нужен Zigbee-выключатель света' },
          },
          states: [{ id: 21, scenario_type: 'BOX_CLIMATE', ac_request_active: true }],
        }],
      }],
      resource_catalog: {
        plants: [{ id: 3, name: 'Томат' }],
      },
    };

    const warnings = listFarmWarnings(overview);
    expect(warnings).toHaveLength(6);
    expect(countFarmWarnings(overview)).toBe(warnings.length);
    expect(warnings.map((warning) => warning.message)).toEqual([
      'нет связи',
      'Есть запрос теплицы на охлаждение',
      'Устройство Zigbee не найдено',
      'Нужен Zigbee-выключатель света',
      'Требуется охлаждение',
      'Не выбрана теплица',
    ]);
  });

  it('vozvrashchaet gotovye drafty scenariev i roli ustrojstva', () => {
    const zone = {
      scenarios: [{
        scenario_type: 'LIGHT_SCHEDULE',
        enabled: true,
        config: { start_time: '08:00' },
      }],
    };
    expect(createScenarioDrafts(zone).LIGHT_SCHEDULE).toMatchObject({
      enabled: true,
      config: { start_time: '08:00', end_time: '22:00' },
    });

    const overview = {
      farms: [{
        id: 1,
        name: 'Ферма',
        greenhouses: [{
          id: 2,
          name: 'Теплица 2',
          slots: [{
            role: 'LIGHT_SWITCH',
            source_type: 'ZIGBEE_DEVICE',
            zigbee_coordinator_id: coordinatorId,
            zigbee_ieee_address: '0x1234',
          }],
        }],
      }],
    };
    expect(assignmentsForZigbeeDevice(overview, {
      coordinator_id: coordinatorId,
      ieee_address: '0x1234',
    })).toEqual([{
      zoneId: 2,
      zoneName: 'Ферма · Теплица 2',
      role: 'LIGHT_SWITCH',
    }]);
  });

  it('ogranichivaet kartochku ustrojstva prioritetnymi metrikami', () => {
    const metrics = [
      { property: 'linkquality' },
      { property: 'battery' },
      { property: 'humidity' },
      { property: 'temperature' },
      { property: 'voltage' },
      { property: 'power' },
      { property: 'energy' },
    ];
    expect(priorityDeviceMetrics({ metrics }).map((item) => item.property))
      .toEqual(['temperature', 'humidity', 'battery', 'power', 'linkquality', 'voltage']);
  });

  it('filtruet ustrojstva po sostoyaniyu i poisku', () => {
    const devices = [
      {
        friendly_name: 'Датчик климата',
        coordinator_name: 'Основной',
        ieee_address: '0x01',
        availability: 'online',
        definition: { vendor: 'Aqara', model: 'WSDCGQ11LM' },
      },
      {
        friendly_name: 'Реле света',
        coordinator_name: 'Основной',
        ieee_address: '0x02',
        availability: 'offline',
        definition: { vendor: 'Sonoff', model: 'ZBMINI' },
      },
    ];

    expect(filterZigbeeDevices(devices, '', 'online')).toHaveLength(1);
    expect(filterZigbeeDevices(devices, 'zbmini', 'all')[0].friendly_name).toBe('Реле света');
    expect(filterZigbeeDevices(devices, '0x01', 'all')[0].friendly_name).toBe('Датчик климата');
  });
});
