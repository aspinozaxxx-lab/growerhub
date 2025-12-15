// Minimal API helper for sensor history and watering logs.
// Uses the shared fetch client pattern from AuthContext (Bearer token if provided).
import { apiFetch } from './client';

export async function fetchSensorHistory(deviceId, hours, token) {
  void token;
  const response = await apiFetch(`/api/device/${encodeURIComponent(deviceId)}/sensor-history?hours=${hours}`);

  if (!response.ok) {
    throw new Error(`Failed to load sensor history (${response.status})`);
  }

  return response.json();
}

export async function fetchWateringLogs(deviceId, days, token) {
  void token;
  const response = await apiFetch(`/api/device/${encodeURIComponent(deviceId)}/watering-logs?days=${days}`);

  if (!response.ok) {
    throw new Error(`Failed to load watering logs (${response.status})`);
  }

  return response.json();
}
