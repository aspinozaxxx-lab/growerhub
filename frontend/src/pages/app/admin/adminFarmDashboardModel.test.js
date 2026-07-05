import { describe, expect, it } from 'vitest';
import {
  RESOURCE_ROLES,
  SCENARIO_TYPES,
  buildAcRequestBoxes,
  findResource,
  formatResourceValue,
  isSwitchResourceOn,
  resourceRoleLabel,
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
});
