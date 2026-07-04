import { describe, expect, it } from 'vitest';
import {
  bindingOptionValue,
  optionsForRole,
  optionsWithCurrentBinding,
  resourcePayload,
} from './adminAutomationResources';

const smartplug2 = {
  friendly_name: 'smartplug2',
  ieee_address: '0xa4c1380000000002',
  definition: {
    vendor: 'Tuya',
    model: 'TS011F_plug_1_1',
  },
  metrics: [
    { property: 'power', value: 0, unit: 'W' },
  ],
  controls: [
    { property: 'state', value: 'OFF' },
  ],
};

const savedLightBinding = {
  role: 'LIGHT_SWITCH',
  source_type: 'ZIGBEE_DEVICE',
  zigbee_ieee_address: smartplug2.ieee_address,
  zigbee_property: 'state',
  command_property: 'state',
  on_value: 'ON',
  off_value: 'OFF',
  label: smartplug2.friendly_name,
};

describe('admin automation resources', () => {
  it('uses the same option value for saved Zigbee switch bindings and catalog options', () => {
    const catalog = { zigbee_devices: [smartplug2] };

    const options = optionsForRole('LIGHT_SWITCH', catalog);

    expect(options).toHaveLength(1);
    expect(options[0].value).toBe(bindingOptionValue(savedLightBinding));
    expect(resourcePayload('LIGHT_SWITCH', options[0].value)).toEqual({
      role: 'LIGHT_SWITCH',
      source_type: 'ZIGBEE_DEVICE',
      native_sensor_id: null,
      native_pump_id: null,
      zigbee_ieee_address: smartplug2.ieee_address,
      zigbee_property: 'state',
      command_property: 'state',
      on_value: 'ON',
      off_value: 'OFF',
    });
  });

  it('keeps an already saved binding visible when the current catalog has no matching option', () => {
    const options = optionsWithCurrentBinding([], savedLightBinding);

    expect(options).toHaveLength(1);
    expect(options[0].value).toBe(bindingOptionValue(savedLightBinding));
    expect(options[0].label).toContain('smartplug2');
  });
});
