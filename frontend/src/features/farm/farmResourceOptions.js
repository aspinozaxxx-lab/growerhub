const SWITCH_ROLES = new Set(['AC_SWITCH', 'EXHAUST_SWITCH', 'LIGHT_SWITCH']);

const OPTION_KEYS = [
  'source_type',
  'native_sensor_id',
  'native_pump_id',
  'zigbee_coordinator_id',
  'zigbee_ieee_address',
  'zigbee_property',
  'command_property',
  'on_value',
  'off_value',
];

function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeOptionPayload(payload = {}) {
  const normalized = OPTION_KEYS.reduce((result, key) => {
    result[key] = payload[key] ?? null;
    return result;
  }, {});
  if (normalized.source_type === 'NATIVE_SENSOR') {
    normalized.native_pump_id = null;
    normalized.zigbee_coordinator_id = null;
    normalized.zigbee_ieee_address = null;
    normalized.zigbee_property = null;
    normalized.command_property = null;
    normalized.on_value = null;
    normalized.off_value = null;
  }
  if (normalized.source_type === 'NATIVE_PUMP') {
    normalized.native_sensor_id = null;
    normalized.zigbee_coordinator_id = null;
    normalized.zigbee_ieee_address = null;
    normalized.zigbee_property = null;
    normalized.command_property = null;
    normalized.on_value = null;
    normalized.off_value = null;
  }
  if (normalized.source_type === 'ZIGBEE_DEVICE') {
    normalized.native_sensor_id = null;
    normalized.native_pump_id = null;
  }
  return normalized;
}

export function optionValue(payload) {
  return JSON.stringify(normalizeOptionPayload(payload));
}

export function parseOptionValue(value) {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

export function bindingOptionValue(binding) {
  if (!binding?.source_type) return '';
  return optionValue({
    source_type: binding.source_type,
    native_sensor_id: binding.native_sensor_id,
    native_pump_id: binding.native_pump_id,
    zigbee_coordinator_id: binding.zigbee_coordinator_id,
    zigbee_ieee_address: binding.zigbee_ieee_address,
    zigbee_property: binding.zigbee_property || binding.command_property,
    command_property: binding.command_property,
    on_value: binding.on_value,
    off_value: binding.off_value,
  });
}

function featureLabel(feature) {
  return feature?.label || feature?.property || feature?.type || '-';
}

function zigbeeModel(device) {
  const definition = device?.definition && typeof device.definition === 'object' ? device.definition : {};
  return [definition.vendor, definition.model].filter(Boolean).join(' / ');
}

function hasWritableState(device) {
  return listOrEmpty(device?.controls).some((feature) => feature.property === 'state');
}

function hasMetric(device, property) {
  return listOrEmpty(device?.metrics).some((feature) => feature.property === property);
}

function hasReadableProperty(device, property) {
  return listOrEmpty(device?.metrics).some((feature) => feature.property === property)
    || listOrEmpty(device?.controls).some((feature) => feature.property === property);
}

function nativeDeviceLabel(devices, deviceId) {
  const device = listOrEmpty(devices).find((item) => item.id === deviceId);
  return device?.name || device?.device_id || `Device ${deviceId}`;
}

export function optionsForRole(role, catalog) {
  const nativeDevices = listOrEmpty(catalog?.native_devices);
  const zigbeeDevices = listOrEmpty(catalog?.zigbee_devices);
  const options = [];

  if ([
    'AIR_TEMPERATURE_SENSOR',
    'AIR_HUMIDITY_SENSOR',
    'SOIL_MOISTURE_SENSOR',
  ].includes(role)) {
    const typeByRole = {
      AIR_TEMPERATURE_SENSOR: 'AIR_TEMPERATURE',
      AIR_HUMIDITY_SENSOR: 'AIR_HUMIDITY',
      SOIL_MOISTURE_SENSOR: 'SOIL_MOISTURE',
    };
    const propertyByRole = {
      AIR_TEMPERATURE_SENSOR: 'temperature',
      AIR_HUMIDITY_SENSOR: 'humidity',
      SOIL_MOISTURE_SENSOR: 'soil_moisture',
    };
    const expectedType = typeByRole[role];
    const zigbeeProperty = propertyByRole[role];
    nativeDevices.forEach((device) => {
      listOrEmpty(device.sensors)
        .filter((sensor) => sensor.type === expectedType)
        .forEach((sensor) => {
          options.push({
            value: optionValue({
              source_type: 'NATIVE_SENSOR',
              native_sensor_id: sensor.id,
            }),
            label: `${sensor.label || expectedType} · ${nativeDeviceLabel(nativeDevices, sensor.device_id)}`,
            currentValue: sensor.last_value,
            lastSeenAt: sensor.last_ts || sensor.last_seen_at,
            connectionStatus: device.is_online === false ? 'offline' : sensor.status,
          });
        });
    });
    zigbeeDevices
      .filter((device) => hasMetric(device, zigbeeProperty))
      .forEach((device) => {
        options.push({
          value: optionValue({
            source_type: 'ZIGBEE_DEVICE',
            zigbee_coordinator_id: device.coordinator_id,
            zigbee_ieee_address: device.ieee_address,
            zigbee_property: zigbeeProperty,
          }),
          label: `${device.friendly_name} · ${featureLabel({ property: zigbeeProperty })}`,
          currentValue: listOrEmpty(device.metrics)
            .find((feature) => feature.property === zigbeeProperty)?.value,
          lastSeenAt: device.last_state_at,
          connectionStatus: device.availability,
        });
      });
  }

  if (role === 'LEAK_SENSOR') {
    zigbeeDevices
      .filter((device) => hasReadableProperty(device, 'water_leak'))
      .forEach((device) => {
        options.push({
          value: optionValue({
            source_type: 'ZIGBEE_DEVICE',
            zigbee_coordinator_id: device.coordinator_id,
            zigbee_ieee_address: device.ieee_address,
            zigbee_property: 'water_leak',
          }),
          label: `${device.friendly_name} · ${featureLabel({ property: 'water_leak' })}`,
          currentValue: listOrEmpty(device.metrics)
            .find((feature) => feature.property === 'water_leak')?.value,
          lastSeenAt: device.last_state_at,
          connectionStatus: device.availability,
        });
      });
  }

  if (SWITCH_ROLES.has(role)) {
    zigbeeDevices
      .filter((device) => hasWritableState(device))
      .forEach((device) => {
        options.push({
          value: optionValue({
            source_type: 'ZIGBEE_DEVICE',
            zigbee_coordinator_id: device.coordinator_id,
            zigbee_ieee_address: device.ieee_address,
            zigbee_property: 'state',
            command_property: 'state',
            on_value: 'ON',
            off_value: 'OFF',
          }),
          label: `${device.friendly_name}${zigbeeModel(device) ? ` · ${zigbeeModel(device)}` : ''}`,
          currentValue: listOrEmpty(device.controls)
            .find((feature) => feature.property === 'state')?.value,
          lastSeenAt: device.last_state_at,
          connectionStatus: device.availability,
        });
      });
  }

  if (role === 'WATER_PUMP') {
    nativeDevices.forEach((device) => {
      listOrEmpty(device.pumps).forEach((pump) => {
        options.push({
          value: optionValue({
            source_type: 'NATIVE_PUMP',
            native_pump_id: pump.id,
          }),
          label: `${pump.label || `Насос ${pump.channel ?? ''}`} · ${nativeDeviceLabel(nativeDevices, pump.device_id)}`,
          currentValue: pump.is_running,
          lastSeenAt: pump.last_seen_at,
          connectionStatus: pump.is_online === false ? 'offline' : 'online',
        });
      });
    });
  }

  return options;
}

export function resourcePayload(role, value) {
  const parsed = parseOptionValue(value);
  if (!parsed) return null;
  const isSwitch = SWITCH_ROLES.has(role);
  return {
    role,
    source_type: parsed.source_type,
    native_sensor_id: parsed.native_sensor_id || null,
    native_pump_id: parsed.native_pump_id || null,
    zigbee_coordinator_id: parsed.zigbee_coordinator_id || null,
    zigbee_ieee_address: parsed.zigbee_ieee_address || null,
    zigbee_property: parsed.zigbee_property || (isSwitch ? 'state' : null),
    command_property: parsed.command_property || (isSwitch ? 'state' : null),
    on_value: parsed.on_value || (isSwitch ? 'ON' : null),
    off_value: parsed.off_value || (isSwitch ? 'OFF' : null),
  };
}

export function physicalChannelKey(payload = {}) {
  if (payload.source_type === 'NATIVE_SENSOR' && payload.native_sensor_id) {
    return `sensor:${payload.native_sensor_id}`;
  }
  if (payload.source_type === 'NATIVE_PUMP' && payload.native_pump_id) {
    return `pump:${payload.native_pump_id}`;
  }
  if (payload.source_type !== 'ZIGBEE_DEVICE' || !payload.zigbee_ieee_address) {
    return null;
  }
  const property = payload.command_property || payload.zigbee_property || 'state';
  return [
    'zigbee',
    payload.zigbee_coordinator_id || '',
    String(payload.zigbee_ieee_address).toLowerCase(),
    property,
  ].join(':');
}

export function resourceBindingForRole(resources, role) {
  return listOrEmpty(resources).find((resource) => resource.role === role) || null;
}

function fallbackBindingLabel(binding) {
  if (!binding) return '';
  const label = binding.label
    || binding.zigbee_ieee_address
    || (binding.native_sensor_id ? 'Native sensor' : null)
    || (binding.native_pump_id ? 'Native pump' : null)
    || binding.role;
  const detail = binding.source_type === 'ZIGBEE_DEVICE' ? binding.zigbee_property : null;
  return [label, detail].filter(Boolean).join(' - ');
}

export function optionsWithCurrentBinding(options, binding) {
  const currentValue = bindingOptionValue(binding);
  if (!currentValue || options.some((option) => option.value === currentValue)) {
    return options;
  }
  return [
    {
      value: currentValue,
      label: fallbackBindingLabel(binding),
    },
    ...options,
  ];
}
