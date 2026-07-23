// API helper for user devices.
import { apiFetch } from './client';
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
