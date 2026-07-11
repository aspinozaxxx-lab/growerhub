export const RESOURCE_ROLES = {
  AC_SWITCH: 'AC_SWITCH',
  AIR_TEMPERATURE_SENSOR: 'AIR_TEMPERATURE_SENSOR',
  EXHAUST_SWITCH: 'EXHAUST_SWITCH',
  LIGHT_SWITCH: 'LIGHT_SWITCH',
  SOIL_MOISTURE_SENSOR: 'SOIL_MOISTURE_SENSOR',
  WATER_PUMP: 'WATER_PUMP',
};

export const SCENARIO_TYPES = {
  ROOM_CLIMATE: 'ROOM_CLIMATE',
  BOX_CLIMATE: 'BOX_CLIMATE',
  LIGHT_SCHEDULE: 'LIGHT_SCHEDULE',
  WATERING: 'WATERING',
};

export const RESOURCE_SOURCE_TYPES = {
  NATIVE_SENSOR: 'NATIVE_SENSOR',
  NATIVE_PUMP: 'NATIVE_PUMP',
  ZIGBEE_DEVICE: 'ZIGBEE_DEVICE',
};

const RESOURCE_ROLE_LABELS = {
  [RESOURCE_ROLES.AC_SWITCH]: 'Кондиционер',
  [RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR]: 'Температура воздуха',
  [RESOURCE_ROLES.EXHAUST_SWITCH]: 'Обдув',
  [RESOURCE_ROLES.LIGHT_SWITCH]: 'Свет',
  [RESOURCE_ROLES.SOIL_MOISTURE_SENSOR]: 'Влажность почвы',
  [RESOURCE_ROLES.WATER_PUMP]: 'Полив',
};

const SCENARIO_TYPE_LABELS = {
  [SCENARIO_TYPES.ROOM_CLIMATE]: 'Климат фермы',
  [SCENARIO_TYPES.BOX_CLIMATE]: 'Климат бокса',
  [SCENARIO_TYPES.LIGHT_SCHEDULE]: 'Свет',
  [SCENARIO_TYPES.WATERING]: 'Полив',
};

const SCENARIO_STATUS_LABELS = {
  active: 'Активно',
  disabled: 'Выключено',
  error: 'Ошибка',
  limited: 'Ограничено',
  pending: 'Ожидает оценки',
  stale: 'Нет актуальных данных',
  unavailable: 'Недоступно',
  unready: 'Не готово',
};

const SENSOR_UNITS = {
  [RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR]: '°C',
  [RESOURCE_ROLES.SOIL_MOISTURE_SENSOR]: '%',
};

const SENSOR_METRICS = {
  [RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR]: 'air_temperature',
  [RESOURCE_ROLES.SOIL_MOISTURE_SENSOR]: 'soil_moisture',
};

const NUMBER_FORMATTER = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 1,
});

export function listOrEmpty(value) {
  return Array.isArray(value) ? value : [];
}

export function findResource(resources, role) {
  return listOrEmpty(resources).find((resource) => resource?.role === role) || null;
}

export function findScenario(scenarios, scenarioType) {
  return listOrEmpty(scenarios).find((scenario) => scenario?.scenario_type === scenarioType) || null;
}

export function findState(states, scenarioType) {
  return listOrEmpty(states).find((state) => state?.scenario_type === scenarioType) || null;
}

export function resourceRoleLabel(role) {
  return RESOURCE_ROLE_LABELS[role] || 'Ресурс';
}

export function scenarioTypeLabel(scenarioType) {
  return SCENARIO_TYPE_LABELS[scenarioType] || 'Сценарий';
}

export function scenarioStatusLabel(status) {
  return SCENARIO_STATUS_LABELS[status] || 'Состояние неизвестно';
}

export function scenarioDisplayStatus(state, scenario) {
  if (!scenario?.enabled) {
    return scenarioStatusLabel('disabled');
  }
  if (!state?.status) {
    return scenarioStatusLabel('pending');
  }
  return scenarioStatusLabel(state.status);
}

export function scenarioTone(state, scenario) {
  if (!scenario?.enabled) return 'muted';
  if (!state?.status) return 'warning';
  if (state.status === 'active') return 'success';
  if (state.status === 'error') return 'danger';
  if (state.status === 'limited' || state.status === 'stale' || state.status === 'unready') return 'warning';
  return 'muted';
}

export function hasCurrentValue(resource) {
  return resource?.current_value !== null
    && resource?.current_value !== undefined
    && resource?.current_value !== '';
}

export function isSwitchResourceOn(resource) {
  if (!hasCurrentValue(resource)) {
    return false;
  }
  const onValue = resource?.on_value || 'ON';
  return String(resource.current_value).toLowerCase() === String(onValue).toLowerCase();
}

export function isPumpResourceRunning(resource) {
  return resource?.current_value === true;
}

export function isEquipmentActive(resource, role) {
  if (role === RESOURCE_ROLES.WATER_PUMP) {
    return isPumpResourceRunning(resource);
  }
  return isSwitchResourceOn(resource);
}

export function switchStateLabel(isActive, hasValue = true) {
  if (!hasValue) {
    return 'Нет данных';
  }
  return isActive ? 'Включено' : 'Выключено';
}

export function resourceReadyLabel(resource) {
  if (!resource) {
    return 'Не привязано';
  }
  if (resource.connection_status === 'warning') {
    return resource.connection_message || 'нет связи';
  }
  return resource.ready ? 'Готово' : 'Не готово';
}

export function resourceTone(resource, isActive = false) {
  if (resource?.connection_status === 'warning') return 'warning';
  if (!resource || !resource.ready) return 'warning';
  return isActive ? 'success' : 'muted';
}

export function formatSensorValue(value, unit = '') {
  if (value === null || value === undefined || value === '' || Number.isNaN(Number(value))) {
    return 'Нет данных';
  }
  return `${NUMBER_FORMATTER.format(Number(value))}${unit ? ` ${unit}` : ''}`;
}

export function formatResourceValue(resource, role) {
  if (!resource) {
    return 'Не привязано';
  }
  if (role === RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR || role === RESOURCE_ROLES.SOIL_MOISTURE_SENSOR) {
    return formatSensorValue(resource.current_value, SENSOR_UNITS[role]);
  }
  if (role === RESOURCE_ROLES.WATER_PUMP) {
    if (!hasCurrentValue(resource)) {
      return 'Нет данных';
    }
    return isPumpResourceRunning(resource) ? 'Идет полив' : 'Ожидание';
  }
  return switchStateLabel(isSwitchResourceOn(resource), hasCurrentValue(resource));
}

export function formatDateTime(value) {
  if (!value) {
    return 'Время неизвестно';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Время неизвестно';
  }
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function buildAcRequestBoxes(room) {
  return listOrEmpty(room?.boxes).filter((box) => {
    const state = findState(box?.states, SCENARIO_TYPES.BOX_CLIMATE);
    return Boolean(state?.ac_request_active);
  });
}

export function countPlantsInRoom(room) {
  return listOrEmpty(room?.boxes).reduce((total, box) => total + listOrEmpty(box?.plants).length, 0);
}

export function resourceStatsProperty(resource, role) {
  if (!resource) {
    return null;
  }
  if (resource.zigbee_property) {
    return resource.zigbee_property;
  }
  if (resource.command_property) {
    return resource.command_property;
  }
  if (role === RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR) {
    return 'temperature';
  }
  if (role === RESOURCE_ROLES.SOIL_MOISTURE_SENSOR) {
    return 'soil_moisture';
  }
  return 'state';
}

export function buildResourceStatsPayload(resource, role, subtitle, scope = {}) {
  if (!resource) {
    return null;
  }
  const title = resourceRoleLabel(role);
  const resolvedSubtitle = subtitle || resource.label || '';
  if (resource.source_type === RESOURCE_SOURCE_TYPES.NATIVE_SENSOR && resource.native_sensor_id) {
    return {
      mode: 'sensor',
      sensorId: resource.native_sensor_id,
      metric: SENSOR_METRICS[role] || 'soil_moisture',
      chartKind: 'numeric',
      title,
      subtitle: resolvedSubtitle,
    };
  }
  if (resource.source_type === RESOURCE_SOURCE_TYPES.NATIVE_PUMP && resource.native_pump_id) {
    return {
      mode: 'box-watering',
      boxId: scope.boxId || null,
      pumpId: resource.native_pump_id,
      title,
      subtitle: resolvedSubtitle,
    };
  }
  if (resource.source_type === RESOURCE_SOURCE_TYPES.ZIGBEE_DEVICE && resource.zigbee_ieee_address) {
    const property = resourceStatsProperty(resource, role);
    if (!property) {
      return null;
    }
    const isSensor = role === RESOURCE_ROLES.AIR_TEMPERATURE_SENSOR || role === RESOURCE_ROLES.SOIL_MOISTURE_SENSOR;
    return {
      mode: 'zigbee',
      zigbeeIeeeAddress: resource.zigbee_ieee_address,
      zigbeeProperty: property,
      metric: SENSOR_METRICS[role] || 'device_state',
      chartKind: isSensor ? 'numeric' : 'binary',
      title,
      subtitle: resolvedSubtitle,
      valueLabel: title,
      binaryOnLabel: 'Включено',
      binaryOffLabel: 'Выключено',
    };
  }
  return null;
}
