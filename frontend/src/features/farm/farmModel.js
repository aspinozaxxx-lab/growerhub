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

export const FARM_ROOM_SLOT_ROLES = ['AC_SWITCH'];

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

const FARM_CLIMATE_DEFAULTS = {
  off_delay_minutes: 5,
  min_toggle_minutes: 5,
};

export const listOrEmpty = (value) => (Array.isArray(value) ? value : []);

export const overviewFarms = (overview) => listOrEmpty(overview?.farms);

export const overviewGreenhouses = (overview) => overviewFarms(overview)
  .flatMap((farm) => listOrEmpty(farm?.greenhouses));

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

export function createFarmClimateDraft(farm) {
  const current = scenarioForType(farm, 'ROOM_CLIMATE');
  return {
    scenario_type: 'ROOM_CLIMATE',
    enabled: Boolean(current?.enabled),
    config: {
      ...FARM_CLIMATE_DEFAULTS,
      ...(current?.config || {}),
    },
  };
}

export function buildSlotOccupancy(scopes) {
  const result = new Map();
  listOrEmpty(scopes).forEach((scope) => {
    listOrEmpty(scope.slots).forEach((slot) => {
      const key = physicalChannelKey(slot);
      if (key) {
        result.set(key, {
          zoneId: scope.id,
          zoneName: scope.name,
          scopeType: slot.scope_type || scope.scope_type || 'BOX',
          role: slot.role,
        });
      }
    });
  });
  return result;
}

export function findSlotConflicts(zoneId, valuesByRole, zones, scopeType = 'BOX') {
  const occupancy = buildSlotOccupancy(zones);
  const conflicts = [];
  Object.entries(valuesByRole || {}).forEach(([role, value]) => {
    const parsed = parseOptionValue(value);
    const key = parsed ? physicalChannelKey(parsed) : null;
    const occupied = key ? occupancy.get(key) : null;
    if (occupied && (occupied.zoneId !== zoneId || occupied.scopeType !== scopeType)) {
      conflicts.push({ ...occupied, role, key });
    }
  });
  return conflicts;
}

export function farmOverviewToDashboardRooms(overview) {
  return overviewFarms(overview).map((farm) => {
    return {
      id: farm.id,
      name: farm.name,
      enabled: farm.enabled,
      resources: listOrEmpty(farm.slots),
      scenarios: listOrEmpty(farm.scenarios),
      states: listOrEmpty(farm.states),
      boxes: listOrEmpty(farm.greenhouses).map((greenhouse) => ({
        id: greenhouse.id,
        name: greenhouse.name,
        enabled: greenhouse.enabled,
        plants: listOrEmpty(greenhouse.plants),
        resources: listOrEmpty(greenhouse.slots),
        scenarios: listOrEmpty(greenhouse.scenarios),
        states: listOrEmpty(greenhouse.states),
        readiness: greenhouse.readiness || {},
        last_actions: listOrEmpty(greenhouse.last_actions),
      })),
      last_actions: listOrEmpty(farm.last_actions),
    };
  });
}

export function findUnassignedFarmPlants(overview) {
  const assignedPlantIds = new Set(
    overviewGreenhouses(overview).flatMap((greenhouse) => (
      listOrEmpty(greenhouse?.plants).map((plant) => plant?.id).filter((id) => id != null)
    )),
  );
  return listOrEmpty(overview?.resource_catalog?.plants)
    .filter((plant) => plant?.id != null && !assignedPlantIds.has(plant.id));
}

export function listFarmWarnings(overview) {
  const warnings = [];
  overviewFarms(overview).forEach((farm) => {
    listOrEmpty(farm.slots)
      .filter((slot) => slot.connection_status === 'warning' || slot.ready === false)
      .forEach((slot) => warnings.push({
        id: `farm:${farm.id}:slot:${slot.id || slot.role}`,
        scopeLabel: 'Ферма',
        scopeName: farm.name,
        label: slot.label
          ? `${SLOT_ROLE_LABELS[slot.role] || slot.role}: ${slot.label}`
          : (SLOT_ROLE_LABELS[slot.role] || slot.role),
        message: slot.connection_message || slot.reason || 'Не готово',
      }));
    listOrEmpty(farm.states)
      .filter((state) => state.scenario_type === 'ROOM_CLIMATE' && state.ac_request_active)
      .forEach((state) => warnings.push({
        id: `farm:${farm.id}:request:${state.id || 'room-climate'}`,
        scopeLabel: 'Ферма',
        scopeName: farm.name,
        label: 'Климат фермы',
        message: 'Есть запрос теплицы на охлаждение',
      }));

    listOrEmpty(farm.greenhouses).forEach((greenhouse) => {
      const scopeName = `${farm.name} · ${greenhouse.name}`;
      listOrEmpty(greenhouse.slots)
        .filter((slot) => slot.connection_status === 'warning' || slot.ready === false)
        .forEach((slot) => warnings.push({
          id: `greenhouse:${greenhouse.id}:slot:${slot.id || slot.role}`,
          scopeLabel: 'Теплица',
          scopeName,
          label: slot.label
            ? `${SLOT_ROLE_LABELS[slot.role] || slot.role}: ${slot.label}`
            : (SLOT_ROLE_LABELS[slot.role] || slot.role),
          message: slot.connection_message || slot.reason || 'Не готово',
        }));
      Object.entries(greenhouse.readiness || {})
        .filter(([, readiness]) => !readiness?.ready)
        .forEach(([scenarioType, readiness]) => warnings.push({
          id: `greenhouse:${greenhouse.id}:readiness:${scenarioType}`,
          scopeLabel: 'Теплица',
          scopeName,
          label: SCENARIO_LABELS[scenarioType] || scenarioType,
          message: readiness?.reason || 'Сценарий не готов',
        }));
      listOrEmpty(greenhouse.states)
        .filter((state) => state.scenario_type === 'BOX_CLIMATE' && state.ac_request_active)
        .forEach((state) => warnings.push({
          id: `greenhouse:${greenhouse.id}:request:${state.id || 'box-climate'}`,
          scopeLabel: 'Теплица',
          scopeName,
          label: 'Климат',
          message: 'Требуется охлаждение',
        }));
    });
  });

  findUnassignedFarmPlants(overview).forEach((plant) => warnings.push({
    id: `plant:${plant.id}:unassigned`,
    scopeLabel: 'Растение',
    scopeName: plant.name || 'Растение без названия',
    label: 'Размещение',
    message: 'Не выбрана теплица',
  }));
  return warnings;
}

export function countFarmWarnings(overview) {
  return listFarmWarnings(overview).length;
}

export function assignmentsForZigbeeDevice(overview, device) {
  if (!device) return [];
  const ieee = String(device.ieee_address || '').toLowerCase();
  const scopes = overviewFarms(overview).flatMap((farm) => [
    { ...farm, assignmentName: farm.name },
    ...listOrEmpty(farm.greenhouses).map((greenhouse) => ({
      ...greenhouse,
      assignmentName: `${farm.name} · ${greenhouse.name}`,
    })),
  ]);
  return scopes.flatMap((scope) => (
    listOrEmpty(scope.slots)
      .filter((slot) => (
        slot.source_type === 'ZIGBEE_DEVICE'
        && String(slot.zigbee_ieee_address || '').toLowerCase() === ieee
        && (!slot.zigbee_coordinator_id
          || !device.coordinator_id
          || slot.zigbee_coordinator_id === device.coordinator_id)
      ))
      .map((slot) => ({
        zoneId: scope.id,
        zoneName: scope.assignmentName,
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
