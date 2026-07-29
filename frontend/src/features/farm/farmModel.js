import {
  parseOptionValue,
  physicalChannelKey,
} from './farmResourceOptions';

export const FARM_SLOT_ROLES = [
  'AC_SWITCH',
  'AIR_TEMPERATURE_SENSOR',
  'AIR_HUMIDITY_SENSOR',
  'SOIL_MOISTURE_SENSOR',
  'LEAK_SENSOR',
  'EXHAUST_SWITCH',
  'LIGHT_SWITCH',
  'WATER_PUMP',
];

export const FARM_SCENARIO_TYPES = [
  'BOX_CLIMATE',
  'LIGHT_SCHEDULE',
  'WATERING',
];

export const SLOT_ROLE_LABELS = {
  AC_SWITCH: 'Кондиционер',
  AIR_TEMPERATURE_SENSOR: 'Температура воздуха',
  AIR_HUMIDITY_SENSOR: 'Влажность воздуха',
  SOIL_MOISTURE_SENSOR: 'Влажность почвы',
  LEAK_SENSOR: 'Протечка',
  EXHAUST_SWITCH: 'Обдув',
  LIGHT_SWITCH: 'Свет',
  WATER_PUMP: 'Насос',
};

export const SCENARIO_LABELS = {
  BOX_CLIMATE: 'Климат',
  LIGHT_SCHEDULE: 'Освещение',
  WATERING: 'Полив',
};

const SCENARIO_DEFAULTS = {
  BOX_CLIMATE: {
    min_c: 24,
    max_c: 28,
    exhaust_off_below_c: 26,
    ac_request_above_c: 30,
    ac_clear_below_c: 27,
    off_delay_minutes: 3,
    min_toggle_minutes: 5,
  },
  LIGHT_SCHEDULE: {
    start_time: '06:00',
    end_time: '22:00',
  },
  WATERING: {
    soil_threshold_percent: 35,
    min_interval_hours: 6,
    max_interval_hours: 24,
    run_seconds: 30,
    daily_max_seconds: 300,
    stop_mode: 'fixed_duration',
  },
};

export const listOrEmpty = (value) => (Array.isArray(value) ? value : []);

export function slotForRole(zone, role) {
  return listOrEmpty(zone?.slots).find((slot) => slot.role === role) || null;
}

export function scenarioForType(zone, scenarioType) {
  return listOrEmpty(zone?.scenarios)
    .find((scenario) => scenario.scenario_type === scenarioType) || null;
}

export function createScenarioDrafts(zone) {
  return Object.fromEntries(FARM_SCENARIO_TYPES.map((scenarioType) => {
    const current = scenarioForType(zone, scenarioType);
    return [scenarioType, {
      scenario_type: scenarioType,
      enabled: Boolean(current?.enabled),
      config: {
        ...SCENARIO_DEFAULTS[scenarioType],
        ...(current?.config || {}),
      },
    }];
  }));
}

export function buildSlotOccupancy(zones) {
  const result = new Map();
  listOrEmpty(zones).forEach((zone) => {
    listOrEmpty(zone.slots).forEach((slot) => {
      const key = physicalChannelKey(slot);
      if (key) {
        result.set(key, {
          zoneId: zone.id,
          zoneName: zone.name,
          role: slot.role,
        });
      }
    });
  });
  return result;
}

export function findSlotConflicts(zoneId, valuesByRole, zones) {
  const occupancy = buildSlotOccupancy(zones);
  const conflicts = [];
  Object.entries(valuesByRole || {}).forEach(([role, value]) => {
    const parsed = parseOptionValue(value);
    const key = parsed ? physicalChannelKey(parsed) : null;
    const occupied = key ? occupancy.get(key) : null;
    if (occupied && occupied.zoneId !== zoneId) {
      conflicts.push({ ...occupied, role, key });
    }
  });
  return conflicts;
}

export function farmOverviewToDashboardRooms(overview, innerName = 'Контур зоны') {
  return listOrEmpty(overview?.farm?.zones).map((zone) => {
    const roomResources = listOrEmpty(zone.slots).filter((slot) => slot.role === 'AC_SWITCH');
    const boxResources = listOrEmpty(zone.slots).filter((slot) => slot.role !== 'AC_SWITCH');
    const boxClimate = scenarioForType(zone, 'BOX_CLIMATE');
    const roomStates = listOrEmpty(zone.states)
      .filter((state) => state.scenario_type === 'ROOM_CLIMATE');
    const boxStates = listOrEmpty(zone.states)
      .filter((state) => state.scenario_type !== 'ROOM_CLIMATE');
    return {
      id: zone.id,
      name: zone.name,
      enabled: zone.enabled,
      resources: roomResources,
      scenarios: boxClimate ? [{
        ...boxClimate,
        scenario_type: 'ROOM_CLIMATE',
      }] : [],
      states: roomStates,
      boxes: [{
        id: `zone-${zone.id}`,
        name: innerName,
        enabled: zone.enabled,
        plants: listOrEmpty(zone.plants),
        resources: boxResources,
        scenarios: listOrEmpty(zone.scenarios),
        states: boxStates,
        readiness: zone.readiness || {},
        last_actions: listOrEmpty(zone.last_actions),
      }],
      last_actions: [],
    };
  });
}

export function countFarmWarnings(overview) {
  return listOrEmpty(overview?.farm?.zones).reduce((total, zone) => {
    const offline = listOrEmpty(zone.slots)
      .filter((slot) => slot.connection_status === 'warning' || slot.ready === false)
      .length;
    const unavailable = Object.values(zone.readiness || {})
      .filter((readiness) => !readiness?.ready)
      .length;
    return total + offline + unavailable;
  }, 0);
}

export function assignmentsForZigbeeDevice(overview, device) {
  if (!device) return [];
  const ieee = String(device.ieee_address || '').toLowerCase();
  return listOrEmpty(overview?.farm?.zones).flatMap((zone) => (
    listOrEmpty(zone.slots)
      .filter((slot) => (
        slot.source_type === 'ZIGBEE_DEVICE'
        && String(slot.zigbee_ieee_address || '').toLowerCase() === ieee
        && (!slot.zigbee_coordinator_id
          || !device.coordinator_id
          || slot.zigbee_coordinator_id === device.coordinator_id)
      ))
      .map((slot) => ({
        zoneId: zone.id,
        zoneName: zone.name,
        role: slot.role,
      }))
  ));
}

const PRIORITY_PROPERTIES = [
  'temperature',
  'humidity',
  'soil_moisture',
  'water_leak',
  'battery',
  'power',
];

export function priorityDeviceMetrics(device, limit = 6) {
  const metrics = listOrEmpty(device?.metrics);
  const byProperty = new Map(metrics.map((metric) => [metric.property, metric]));
  const result = PRIORITY_PROPERTIES
    .map((property) => byProperty.get(property))
    .filter(Boolean);
  metrics.forEach((metric) => {
    if (result.length < limit && !result.includes(metric)) {
      result.push(metric);
    }
  });
  return result.slice(0, limit);
}

export function filterZigbeeDevices(devices, query = '', availability = 'all') {
  const normalizedQuery = String(query).trim().toLocaleLowerCase();
  return listOrEmpty(devices).filter((device) => {
    if (availability !== 'all' && device?.availability !== availability) {
      return false;
    }
    if (!normalizedQuery) {
      return true;
    }
    const definition = device?.definition && typeof device.definition === 'object'
      ? device.definition
      : {};
    const searchable = [
      device?.friendly_name,
      device?.coordinator_name,
      device?.ieee_address,
      device?.type,
      definition.vendor,
      definition.model,
      definition.description,
    ].filter(Boolean).join(' ').toLocaleLowerCase();
    return searchable.includes(normalizedQuery);
  });
}
