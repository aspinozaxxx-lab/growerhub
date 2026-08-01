// API helper dlya istorii sensorov.
import { apiFetch } from './client';
import { translateApp } from '../locales/i18n';

export async function fetchSensorHistory(sensorId, hours, token) {
  void token;
  const response = await apiFetch(`/api/sensors/${encodeURIComponent(sensorId)}/history?hours=${hours}`);
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить историю датчика ({{value1}})", { value1: response.status }));
  }
  return response.json();
}
