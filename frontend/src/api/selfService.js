import { apiFetch, normalizeApiErrorMessage } from './client';

async function requestJson(url, init = {}) {
  const headers = new Headers(init.headers || {});
  headers.set('Accept', 'application/json');
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await apiFetch(url, { ...init, headers });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(normalizeApiErrorMessage(data.detail || data.message, {
      status: response.status,
    }));
    error.status = response.status;
    error.code = data.code;
    throw error;
  }
  return data;
}

export const fetchOnboardingStatus = () => requestJson('/api/onboarding/status');

export const completeOnboarding = () => requestJson('/api/onboarding/complete', { method: 'POST' });

export const fetchCoordinators = () => requestJson('/api/zigbee/coordinators');

export const createCoordinator = (name) => requestJson('/api/zigbee/coordinators', {
  method: 'POST',
  body: JSON.stringify({ name }),
});

export const rotateCoordinatorCredentials = (coordinatorId) => requestJson(
  `/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}/credentials/rotate`,
  { method: 'POST' },
);

export const archiveCoordinator = async (coordinatorId) => {
  const response = await apiFetch(`/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}`, {
    method: 'DELETE',
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = new Error(normalizeApiErrorMessage(data.detail || data.message, {
      status: response.status,
    }));
    error.status = response.status;
    throw error;
  }
};

export const fetchCoordinatorOverview = (coordinatorId) => requestJson(
  `/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}/overview`,
);

export const fetchZigbeeHistory = (coordinatorId, ieeeAddress, property, hours = 24) => requestJson(
  `/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}/devices/${encodeURIComponent(ieeeAddress)}/history`
    + `?property=${encodeURIComponent(property)}&hours=${encodeURIComponent(hours)}`,
);

export const enablePermitJoin = (coordinatorId, seconds = 180) => requestJson(
  `/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}/permit-join`,
  {
    method: 'POST',
    body: JSON.stringify({ seconds }),
  },
);

export const setZigbeeProperty = (coordinatorId, ieeeAddress, property, value) => requestJson(
  `/api/zigbee/coordinators/${encodeURIComponent(coordinatorId)}/devices/${encodeURIComponent(ieeeAddress)}/set`,
  {
    method: 'POST',
    body: JSON.stringify({ property, value }),
  },
);

export const fetchFarmOverview = () => requestJson('/api/automation/farm');

export const fetchFarmsOverview = () => requestJson('/api/automation/farms');

export const fetchResourceStatistics = (resourceId, hours = 24) => requestJson(
  `/api/automation/resources/${encodeURIComponent(resourceId)}/statistics`
    + `?hours=${encodeURIComponent(hours)}`,
);

export const createUserFarm = (payload) => requestJson('/api/automation/farms', {
  method: 'POST',
  body: JSON.stringify(payload),
});

export const updateUserFarm = (farmId, payload) => requestJson(
  `/api/automation/farms/${encodeURIComponent(farmId)}`,
  { method: 'PUT', body: JSON.stringify(payload) },
);

export const deleteUserFarm = (farmId) => requestJson(
  `/api/automation/farms/${encodeURIComponent(farmId)}`,
  { method: 'DELETE' },
);

export const createGreenhouse = (farmId, payload) => requestJson(
  `/api/automation/farms/${encodeURIComponent(farmId)}/greenhouses`,
  { method: 'POST', body: JSON.stringify(payload) },
);

export const updateGreenhouse = (greenhouseId, payload) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}`,
  { method: 'PUT', body: JSON.stringify(payload) },
);

export const deleteGreenhouse = (greenhouseId) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}`,
  { method: 'DELETE' },
);

export const replaceUserFarmSlots = (farmId, slots, reassign = false) => requestJson(
  `/api/automation/farms/${encodeURIComponent(farmId)}/slots`,
  {
    method: 'PUT',
    body: JSON.stringify({ slots, reassign }),
  },
);

export const replaceUserFarmScenarios = (farmId, scenarios) => requestJson(
  `/api/automation/farms/${encodeURIComponent(farmId)}/scenarios`,
  {
    method: 'PUT',
    body: JSON.stringify({ scenarios }),
  },
);

export const replaceGreenhouseSlots = (greenhouseId, slots, reassign = false) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}/slots`,
  {
    method: 'PUT',
    body: JSON.stringify({ slots, reassign }),
  },
);

export const replaceGreenhousePlants = (greenhouseId, items) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}/plants`,
  {
    method: 'PUT',
    body: JSON.stringify({ items }),
  },
);

export const updateGreenhousePlantWateringRate = (greenhouseId, plantId, rateMlPerHour) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}`
    + `/plants/${encodeURIComponent(plantId)}/watering-rate`,
  {
    method: 'PATCH',
    body: JSON.stringify({ rate_ml_per_hour: rateMlPerHour }),
  },
);

export const replaceGreenhouseScenarios = (greenhouseId, scenarios) => requestJson(
  `/api/automation/greenhouses/${encodeURIComponent(greenhouseId)}/scenarios`,
  {
    method: 'PUT',
    body: JSON.stringify({ scenarios }),
  },
);

export const createFarm = (name) => requestJson('/api/automation/farm', {
  method: 'POST',
  body: JSON.stringify({ name }),
});

export const updateFarm = (name) => requestJson('/api/automation/farm', {
  method: 'PUT',
  body: JSON.stringify({ name }),
});

export const createFarmZone = (payload) => requestJson('/api/automation/farm/zones', {
  method: 'POST',
  body: JSON.stringify(payload),
});

export const updateFarmZone = (zoneId, payload) => requestJson(
  `/api/automation/farm/zones/${encodeURIComponent(zoneId)}`,
  { method: 'PUT', body: JSON.stringify(payload) },
);

export const deleteFarmZone = (zoneId) => requestJson(
  `/api/automation/farm/zones/${encodeURIComponent(zoneId)}`,
  { method: 'DELETE' },
);

export const replaceFarmZoneSlots = (zoneId, slots, reassign = false) => requestJson(
  `/api/automation/farm/zones/${encodeURIComponent(zoneId)}/slots`,
  {
    method: 'PUT',
    body: JSON.stringify({ slots, reassign }),
  },
);

export const replaceFarmZonePlants = (zoneId, items) => requestJson(
  `/api/automation/farm/zones/${encodeURIComponent(zoneId)}/plants`,
  {
    method: 'PUT',
    body: JSON.stringify({ items }),
  },
);

export const replaceFarmZoneScenarios = (zoneId, scenarios) => requestJson(
  `/api/automation/farm/zones/${encodeURIComponent(zoneId)}/scenarios`,
  {
    method: 'PUT',
    body: JSON.stringify({ scenarios }),
  },
);
