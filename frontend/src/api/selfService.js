import { apiFetch, normalizeApiErrorMessage } from './client';
import { translateApp } from '../locales/i18n';

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

export const fetchAutomationOverview = () => requestJson('/api/automation');

export const createZone = (name) => requestJson('/api/automation/zones', {
  method: 'POST',
  body: JSON.stringify({ name, enabled: true }),
});

export const createZoneSection = (zoneId, name = translateApp("Основная зона")) => requestJson(
  `/api/automation/zones/${encodeURIComponent(zoneId)}/sections`,
  {
    method: 'POST',
    body: JSON.stringify({ name, enabled: true }),
  },
);

export const replaceSectionResources = (sectionId, resources) => requestJson(
  `/api/automation/sections/${encodeURIComponent(sectionId)}/resources`,
  {
    method: 'PUT',
    body: JSON.stringify({ resources }),
  },
);

export const replaceSectionScenarios = (sectionId, scenarios) => requestJson(
  `/api/automation/sections/${encodeURIComponent(sectionId)}/scenarios`,
  {
    method: 'PUT',
    body: JSON.stringify({ scenarios }),
  },
);

export const updateZone = (zoneId, payload) => requestJson(
  `/api/automation/zones/${encodeURIComponent(zoneId)}`,
  { method: 'PUT', body: JSON.stringify(payload) },
);

export const deleteZone = (zoneId) => requestJson(
  `/api/automation/zones/${encodeURIComponent(zoneId)}`,
  { method: 'DELETE' },
);
