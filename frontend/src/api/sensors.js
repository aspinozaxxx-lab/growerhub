// API helper dlya sensors (bindings + history).
import { apiFetch } from './client';

async function parseOptionalJson(response) {
  if (!response || response.status === 204 || response.status === 205) {
    return null;
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  return JSON.parse(text);
}

export async function updateSensorBindings(sensorId, plantIds, token) {
  void token;
  const headers = {
    'Content-Type': 'application/json',
  };
  const response = await apiFetch(`/api/sensors/${encodeURIComponent(sensorId)}/bindings`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ plant_ids: Array.isArray(plantIds) ? plantIds : [] }),
  });
  if (!response.ok) {
    throw new Error(`Failed to update sensor bindings (${response.status})`);
  }
  return parseOptionalJson(response);
}

export async function fetchSensorHistory(sensorId, hours, token) {
  void token;
  const response = await apiFetch(`/api/sensors/${encodeURIComponent(sensorId)}/history?hours=${hours}`);
  if (!response.ok) {
    throw new Error(`Failed to load sensor history (${response.status})`);
  }
  return response.json();
}
