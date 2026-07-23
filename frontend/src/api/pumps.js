// API helper dlya polzovatelskogo ruchnogo poliva.
import { apiFetch } from './client';

export async function startPumpWatering({ pumpId, durationS, waterVolumeL, ph, fertilizersPerLiter, token }) {
  void token;
  const headers = {
    'Content-Type': 'application/json',
  };
  const payload = {};
  if (durationS !== undefined && durationS !== null) {
    payload.duration_s = durationS;
  }
  if (waterVolumeL !== undefined && waterVolumeL !== null) {
    payload.water_volume_l = waterVolumeL;
  }
  if (ph !== undefined && ph !== null && ph !== '') {
    payload.ph = ph;
  }
  if (fertilizersPerLiter) {
    payload.fertilizers_per_liter = fertilizersPerLiter;
  }
  const response = await apiFetch(`/api/pumps/${encodeURIComponent(pumpId)}/watering/start`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Не удалось запустить полив (${response.status})`);
  }
  return response.json();
}

export async function stopPumpWatering(pumpId, token) {
  void token;
  const response = await apiFetch(`/api/pumps/${encodeURIComponent(pumpId)}/watering/stop`, {
    method: 'POST',
  });
  if (!response.ok) {
    throw new Error(`Не удалось остановить полив (${response.status})`);
  }
  return response.json();
}

export async function fetchPumpWateringStatus(pumpId, token) {
  void token;
  const response = await apiFetch(`/api/pumps/${encodeURIComponent(pumpId)}/watering/status`);
  if (!response.ok) {
    throw new Error(`Не удалось загрузить состояние полива (${response.status})`);
  }
  return response.json();
}
