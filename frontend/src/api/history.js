// Minimal API helper for sensor history and watering logs.
// Uses the shared fetch client pattern from AuthContext (Bearer token if provided).
export async function fetchSensorHistory(deviceId, hours, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/device/${encodeURIComponent(deviceId)}/sensor-history?hours=${hours}`, {
    headers,
  });

  if (!response.ok) {
    throw new Error(`Failed to load sensor history (${response.status})`);
  }

  return response.json();
}

export async function fetchWateringLogs(deviceId, days, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/device/${encodeURIComponent(deviceId)}/watering-logs?days=${days}`, {
    headers,
  });

  if (!response.ok) {
    throw new Error(`Failed to load watering logs (${response.status})`);
  }

  return response.json();
}
