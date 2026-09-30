import { describe, expect, it } from 'vitest';
import {
  buildSectionResources,
  encodeFeatureChoice,
  getReadableFeatures,
  getWritableSwitches,
} from './onboardingModel';

const overview = {
  devices: [{
    ieee_address: 'synthetic-device',
    friendly_name: 'Датчик 1',
    metrics: [{ property: 'temperature', label: 'Температура' }],
    controls: [{ property: 'state', value_on: 'ON', value_off: 'OFF' }],
  }],
};

describe('onboardingModel', () => {
  it('выбирает только читаемую температуру и writable state', () => {
    expect(getReadableFeatures(overview, 'temperature')).toHaveLength(1);
    expect(getWritableSwitches(overview)).toHaveLength(1);
  });

  it('создаёт tenant-aware bindings без лишних идентификаторов', () => {
    const choice = encodeFeatureChoice(overview.devices[0], { property: 'temperature' });
    const switchChoice = encodeFeatureChoice(overview.devices[0], { property: 'state' });
    const resources = buildSectionResources({
      coordinatorId: 'coordinator-public-id',
      temperatureChoice: choice,
      lightChoice: switchChoice,
      overview,
    });

    expect(resources).toEqual([
      expect.objectContaining({ role: 'AIR_TEMPERATURE_SENSOR', zigbee_coordinator_id: 'coordinator-public-id' }),
      expect.objectContaining({ role: 'LIGHT_SWITCH', command_property: 'state' }),
    ]);
  });
});
