// API helper for user devices.
import { apiFetch } from './client';

export async function fetchMyDevices(token) {
  void token;
  const response = await apiFetch('/api/devices/my');
  if (!response.ok) {
    throw new Error(`Failed to load devices (${response.status})`);
  }
  return response.json();
}

export async function fetchDeviceSettings(deviceId, token) {
  void token;
  const response = await apiFetch(`/api/device/${encodeURIComponent(deviceId)}/settings`);
  if (!response.ok) {
    throw new Error(`Failed to load device settings (${response.status})`);
  }
  return response.json();
}

export async function updateDeviceSettings(deviceId, settings, token) {
  void token;
  const headers = {
    'Content-Type': 'application/json',
  };
  const response = await apiFetch(`/api/device/${encodeURIComponent(deviceId)}/settings`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(settings),
  });
  if (!response.ok) {
    throw new Error(`Failed to update device (${response.status})`);
  }
  return response.json();
}
