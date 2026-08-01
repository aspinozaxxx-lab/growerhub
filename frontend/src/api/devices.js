// API helper for user devices.
import { apiFetch, readApiErrorMessage } from './client';
import { translateApp } from '../locales/i18n';

export async function fetchMyDevices(token) {
  void token;
  const response = await apiFetch('/api/devices/my');
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить устройства ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

export async function fetchDeviceSettings(deviceId, token) {
  void token;
  const response = await apiFetch(`/api/device/${encodeURIComponent(deviceId)}/settings`);
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить настройки устройства ({{value1}})", { value1: response.status }));
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
    throw new Error(translateApp("Не удалось сохранить устройство ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

export async function claimDevice(deviceId, token) {
  void token;
  const response = await apiFetch('/api/devices/claim', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ device_id: deviceId }),
  });
  if (!response.ok) {
    const error = new Error(await readApiErrorMessage(response, 'Не удалось добавить устройство'));
    error.status = response.status;
    const retryAfter = Number.parseInt(response.headers.get('Retry-After') || '', 10);
    error.retryAfterSeconds = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null;
    throw error;
  }
  return response.json();
}
