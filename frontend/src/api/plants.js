// API helper for plants list.
import { apiFetch } from './client';
import { translateApp } from '../locales/i18n';

export async function fetchPlants(token) {
  void token;
  const response = await apiFetch('/api/plants');
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить растения ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: zagruzka odnogo rastenija po id.
export async function fetchPlant(token, plantId) {
  void token;
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}`);
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить растение ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: sozdanie rastenija (name, planted_at, plant_group_id, plant_type, strain, growth_stage).
export async function createPlant(token, payload) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch('/api/plants', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось создать растение ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: obnovlenie rastenija po id (name, planted_at, plant_group_id, plant_type, strain, growth_stage).
export async function updatePlant(token, plantId, payload) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось сохранить растение ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: udalenie rastenija po id.
export async function deletePlant(token, plantId) {
  void token;
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}`, {
    method: 'DELETE',
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось удалить растение ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: sbornaja komanda urozhaya (text, harvested_at).
export async function harvestPlant(plantId, payload, token) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}/harvest`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось завершить выращивание ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: zagruzka spiska grupp rastenij.
export async function fetchPlantGroups(token) {
  void token;
  const response = await apiFetch('/api/plant-groups');
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить группы растений ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: sozdanie gruppy rastenij (pole name).
export async function createPlantGroup(token, payload) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch('/api/plant-groups', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось создать группу растений ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: pereimenovanie gruppy rastenij po id (pole name).
export async function updatePlantGroup(token, groupId, payload) {
  void token;
  const headers = { 'Content-Type': 'application/json' };
  const response = await apiFetch(`/api/plant-groups/${encodeURIComponent(groupId)}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось сохранить группу растений ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: udalenie gruppy rastenij po id.
export async function deletePlantGroup(token, groupId) {
  void token;
  const response = await apiFetch(`/api/plant-groups/${encodeURIComponent(groupId)}`, {
    method: 'DELETE',
  });
  if (!response.ok) {
    throw new Error(translateApp("Не удалось удалить группу растений ({{value1}})", { value1: response.status }));
  }
  return response.json();
}

// Translitem: istoriya metrik rastenija.
export async function fetchPlantHistory(plantId, hours, metrics, token) {
  void token;
  const metricsParam = metrics ? `&metrics=${encodeURIComponent(metrics)}` : '';
  const response = await apiFetch(`/api/plants/${encodeURIComponent(plantId)}/history?hours=${hours}${metricsParam}`);
  if (!response.ok) {
    throw new Error(translateApp("Не удалось загрузить историю растения ({{value1}})", { value1: response.status }));
  }
  return response.json();
}
