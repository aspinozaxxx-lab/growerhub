// API helper for plants list.
export async function fetchPlants(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch('/api/plants', { headers });
  if (!response.ok) {
    throw new Error(`Failed to load plants (${response.status})`);
  }
  return response.json();
}

// Translitem: zagruzka odnogo rastenija po id.
export async function fetchPlant(token, plantId) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}`, { headers });
  if (!response.ok) {
    throw new Error(`Failed to load plant (${response.status})`);
  }
  return response.json();
}

// Translitem: sozdanie rastenija (name, planted_at, plant_group_id, plant_type, strain, growth_stage).
export async function createPlant(token, payload) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch('/api/plants', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to create plant (${response.status})`);
  }
  return response.json();
}

// Translitem: obnovlenie rastenija po id (name, planted_at, plant_group_id, plant_type, strain, growth_stage).
export async function updatePlant(token, plantId, payload) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to update plant (${response.status})`);
  }
  return response.json();
}

// Translitem: udalenie rastenija po id.
export async function deletePlant(token, plantId) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/plants/${encodeURIComponent(plantId)}`, {
    method: 'DELETE',
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to delete plant (${response.status})`);
  }
  return response.json();
}

// Translitem: zagruzka spiska grupp rastenij.
export async function fetchPlantGroups(token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch('/api/plant-groups', { headers });
  if (!response.ok) {
    throw new Error(`Failed to load plant groups (${response.status})`);
  }
  return response.json();
}

// Translitem: sozdanie gruppy rastenij (pole name).
export async function createPlantGroup(token, payload) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch('/api/plant-groups', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to create plant group (${response.status})`);
  }
  return response.json();
}

// Translitem: pereimenovanie gruppy rastenij po id (pole name).
export async function updatePlantGroup(token, groupId, payload) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(`/api/plant-groups/${encodeURIComponent(groupId)}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Failed to update plant group (${response.status})`);
  }
  return response.json();
}

// Translitem: udalenie gruppy rastenij po id.
export async function deletePlantGroup(token, groupId) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`/api/plant-groups/${encodeURIComponent(groupId)}`, {
    method: 'DELETE',
    headers,
  });
  if (!response.ok) {
    throw new Error(`Failed to delete plant group (${response.status})`);
  }
  return response.json();
}

