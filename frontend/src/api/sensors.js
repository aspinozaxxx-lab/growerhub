// API helper dlya sensors (bindings + history).
import { apiFetch } from './client';

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
  return response.status === 204 ? null : response.json();
}

export async function fetchSensorHistory(sensorId, hours, token) {
  void token;
  const response = await apiFetch(`/api/sensors/${encodeURIComponent(sensorId)}/history?hours=${hours}`);
  if (!response.ok) {
    throw new Error(`Failed to load sensor history (${response.status})`);
  }
  return response.json();
}
