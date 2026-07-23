// API helper dlya sensors (bindings + history).
import { apiFetch } from './client';
import { translateApp } from '../locales/i18n';

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
    throw new Error(translateApp("Не удалось сохранить привязки датчика ({{value1}})", { value1: response.status }));
  }
  return parseOptionalJson(response);
}

export async function fetchSensorHistory(sensorId, hours, token) {
  void token;
  const response = await apiFetch(`/api/sensors/${encodeURIComponent(sensorId)}/history?hours=${hours}`);
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить историю датчика ({{value1}})", { value1: response.status }));
  }
  return response.json();
}
