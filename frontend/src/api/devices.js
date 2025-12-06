// API helper for user devices.
export async function fetchMyDevices(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch('/api/devices/my', { headers });
  if (!response.ok) {
    throw new Error(`Failed to load devices (${response.status})`);
  }
  return response.json();
}

export async function updateDeviceSettings(deviceId, settings, token) {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/device/${encodeURIComponent(deviceId)}/settings`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(settings),
  });
  if (!response.ok) {
    throw new Error(`Failed to update device (${response.status})`);
  }
  return response.json();
}

export async function assignDeviceToPlant(deviceId, plantId, token) {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}/devices/${encodeURIComponent(deviceId)}`, {
    method: 'POST',
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to assign device (${response.status})`);
  }
  return response.json();
}

export async function unassignDeviceFromPlant(deviceId, plantId, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}/devices/${encodeURIComponent(deviceId)}`, {
    method: 'DELETE',
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to unassign device (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}
