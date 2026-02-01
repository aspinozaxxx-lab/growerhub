import { apiFetch } from './client';

// Translitem: fallback-soobshchenie dlya oshibok admin API.
const DEFAULT_ERROR_MESSAGE = 'Ne udalos vypolnit zapros';

// Translitem: chitaem detail iz JSON-otveta ili vozvraschaem fallback.
async function readErrorDetail(response, fallback) {
  try {
    const data = await response.json();
    if (data && data.detail) {
      return data.detail;
    }
  } catch (err) {
    // Translitem: ignoriruem oshibku parse JSON.
  }
  return fallback;
}

// Translitem: poluchaem spisok polzovateley dlya admin-tablicy.
export async function fetchAdminUsers(token) {
  const response = await apiFetch('/api/users', token ? {
    headers: { Authorization: `Bearer ${token}` },
  } : undefined);
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos zagruzit polzovateley');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: sozdanie polzovatelya dlya admin-razdela.
export async function createAdminUser(payload, token) {
  const response = await apiFetch('/api/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos sozdat polzovatelya');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: obnovlenie polzovatelya v admin-razdele.
export async function updateAdminUser(userId, payload, token) {
  const response = await apiFetch(`/api/users/${userId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos sohranit izmeneniya');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: udalenie polzovatelya v admin-razdele.
export async function deleteAdminUser(userId, token) {
  const response = await apiFetch(`/api/users/${userId}`, {
    method: 'DELETE',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos udalit polzovatelya');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return true;
}

// Translitem: poluchaem spisok ustroystv dlya admin-tablicy.
export async function fetchAdminDevices(token) {
  const response = await apiFetch('/api/admin/devices', token ? {
    headers: { Authorization: `Bearer ${token}` },
  } : undefined);
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos zagruzit ustroystva');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: privyazka ustroystva k polzovatelyu (admin).
export async function adminAssignDevice(deviceId, userId, token) {
  const response = await apiFetch(`/api/admin/devices/${deviceId}/assign`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ user_id: userId }),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos privyazat ustroystvo');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: otvyazka ustroystva ot polzovatelya (admin).
export async function adminUnassignDevice(deviceId, token) {
  const response = await apiFetch(`/api/admin/devices/${deviceId}/unassign`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos otvyazat ustroystvo');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: udalenie ustroystva v admin-razdele.
export async function deleteAdminDevice(deviceId, token) {
  const response = await apiFetch(`/api/admin/devices/${encodeURIComponent(deviceId)}`, {
    method: 'DELETE',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos udalit ustroystvo');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return true;
}

// Translitem: poluchaem spisok rasteniy dlya admin-tablicy.
export async function fetchAdminPlants(token) {
  const response = await apiFetch('/api/admin/plants', token ? {
    headers: { Authorization: `Bearer ${token}` },
  } : undefined);
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Ne udalos zagruzit rasteniya');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}
