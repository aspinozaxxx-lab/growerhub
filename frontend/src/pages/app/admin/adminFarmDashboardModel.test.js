import { describe, expect, it } from 'vitest';
import {
  RESOURCE_ROLES,
  RESOURCE_SOURCE_TYPES,
  SCENARIO_TYPES,
  buildAcRequestBoxes,
  buildResourceStatsPayload,
  findResource,
  formatResourceValue,
  isSwitchResourceOn,
  resourceRoleLabel,
  resourceReadyLabel,
  resourceTone,
  scenarioDisplayStatus,
  scenarioStatusLabel,
  scenarioTypeLabel,
  switchStateLabel,
} from './adminFarmDashboardModel';

describe('admin farm dashboard model', () => {
  it('nahodit resurs po roli', () => {
    const resources = [
      { role: RESOURCE_ROLES.LIGHT_SWITCH, current_value: 'OFF' },
      { role: RESOURCE_ROLES.AC_SWITCH, current_value: 'ON' },
    ];

    expect(findResource(resources, RESOURCE_ROLES.AC_SWITCH)).toEqual(resources[1]);
    expect(findResource(resources, RESOURCE_ROLES.WATER_PUMP)).toBeNull();
  });

  it('opredelyaet vklyuchennoe sostoyanie bez pokaza tehnicheskih znachenij', () => {
    expect(isSwitchResourceOn({ current_value: 'on', on_value: 'ON' })).toBe(true);
    expect(isSwitchResourceOn({ current_value: 'OFF', on_value: 'ON' })).toBe(false);
    expect(switchStateLabel(true)).toBe('Включено');
    expect(switchStateLabel(false)).toBe('Выключено');
  });

  it('sobiraet aktivnye zaprosy na kondicioner ot boksov', () => {
    const room = {
      boxes: [
        {
          name: 'Бокс 1',
          states: [{ scenario_type: SCENARIO_TYPES.BOX_CLIMATE, ac_request_active: true }],
        },
        {
          name: 'Бокс 2',
          states: [{ scenario_type: SCENARIO_TYPES.BOX_CLIMATE, ac_request_active: false }],
        },
      ],
    };

    expect(buildAcRequestBoxes(room).map((box) => box.name)).toEqual(['Бокс 1']);
  });

  it('vozvraschaet russkie podpisi dlya rolej scenariev i statusov', () => {
    expect(resourceRoleLabel(RESOURCE_ROLES.AC_SWITCH)).toBe('Кондиционер');
    expect(scenarioTypeLabel(SCENARIO_TYPES.BOX_CLIMATE)).toBe('Климат бокса');
    expect(scenarioStatusLabel('active')).toBe('Активно');
    expect(scenarioDisplayStatus(null, { enabled: true })).toBe('Ожидает оценки');
    expect(scenarioDisplayStatus({ status: 'stale' }, { enabled: true })).toBe('Нет актуальных данных');
  });

  it('formatiruet znacheniya resursov russkimi sostoyaniyami', () => {
    expect(formatResourceValue({
      current_value: 24.35,
      ready: true,
    }, RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR)).toBe('24,4 °C');
    expect(formatResourceValue({
      current_value: true,
      ready: true,
    }, RESOURCE_ROLES.WATER_PUMP)).toBe('Идет полив');
    expect(formatResourceValue(null, RESOURCE_ROLES.LIGHT_SWITCH)).toBe('Не привязано');
  });

  it('pokazyvaet net svyazi kak warning status resursa', () => {
    const resource = {
      ready: true,
      connection_status: 'warning',
      connection_message: 'нет связи',
    };

    expect(resourceReadyLabel(resource)).toBe('нет связи');
    expect(resourceTone(resource, false)).toBe('warning');
  });

  it('stroitr payload statistiki dlya native sensora', () => {
    expect(buildResourceStatsPayload({
      source_type: RESOURCE_SOURCE_TYPES.NATIVE_SENSOR,
      native_sensor_id: 12,
      label: 'Датчик 1',
    }, RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR, 'Бокс 1')).toMatchObject({
      mode: 'sensor',
      sensorId: 12,
      metric: 'air_temperature',
      chartKind: 'numeric',
      title: 'Температура воздуха',
      subtitle: 'Бокс 1',
    });
  });

  it('stroitr payload statistiki dlya Zigbee svojstva', () => {
    expect(buildResourceStatsPayload({
      source_type: RESOURCE_SOURCE_TYPES.ZIGBEE_DEVICE,
      zigbee_ieee_address: '0xabc',
      zigbee_property: 'state',
    }, RESOURCE_ROLES.LIGHT_SWITCH, 'Бокс 2')).toMatchObject({
      mode: 'zigbee',
      zigbeeIeeeAddress: '0xabc',
      zigbeeProperty: 'state',
      metric: 'device_state',
      chartKind: 'binary',
      title: 'Свет',
      subtitle: 'Бокс 2',
    });
  });

  it('stroitr payload statistiki dlya native nasosa', () => {
    expect(buildResourceStatsPayload({
      source_type: RESOURCE_SOURCE_TYPES.NATIVE_PUMP,
      native_pump_id: 3,
    }, RESOURCE_ROLES.WATER_PUMP, 'Бокс 3')).toMatchObject({
      mode: 'pump',
      pumpId: 3,
      metric: 'pump',
      chartKind: 'binary',
      title: 'Полив',
      subtitle: 'Бокс 3',
    });
  });
});
