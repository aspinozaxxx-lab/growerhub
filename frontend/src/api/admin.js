import { apiFetch } from './client';

// Translitem: fallback-soobshchenie dlya oshibok admin API.
const DEFAULT_ERROR_MESSAGE = 'Не удалось выполнить запрос';

// Translitem: chitaem detail iz JSON-otveta ili vozvraschaem fallback.
async function readErrorDetail(response, fallback) {
  try {
    const data = await response.json();
    if (data && data.detail) {
      return data.detail;
    }
  } catch {
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
    const message = await readErrorDetail(response, 'Не удалось загрузить пользователей');
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
    const message = await readErrorDetail(response, 'Не удалось создать пользователя');
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
    const message = await readErrorDetail(response, 'Не удалось сохранить изменения');
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
    const message = await readErrorDetail(response, 'Не удалось удалить пользователя');
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
    const message = await readErrorDetail(response, 'Не удалось загрузить устройства');
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
    const message = await readErrorDetail(response, 'Не удалось привязать устройство');
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
    const message = await readErrorDetail(response, 'Не удалось отвязать устройство');
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
    const message = await readErrorDetail(response, 'Не удалось удалить устройство');
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
    const message = await readErrorDetail(response, 'Не удалось загрузить растения');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: poluchaem poslednie MQTT-soobshcheniya dlya admin-vkladki.
export async function fetchAdminMqttMessages(filters = {}, token) {
  const params = new URLSearchParams();
  if (filters.topic) {
    params.set('topic', filters.topic);
  }
  if (filters.sender) {
    params.set('sender', filters.sender);
  }
  if (filters.limit) {
    params.set('limit', filters.limit);
  }
  const query = params.toString();
  const response = await apiFetch(`/api/admin/mqtt/messages${query ? `?${query}` : ''}`, token ? {
    headers: { Authorization: `Bearer ${token}` },
  } : undefined);
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Не удалось загрузить MQTT сообщения');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: poluchaem Zigbee2MQTT snapshot dlya admin-vkladki.
export async function fetchAdminZigbeeOverview(token) {
  const response = await apiFetch('/api/admin/zigbee', token ? {
    headers: { Authorization: `Bearer ${token}` },
  } : undefined);
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Не удалось загрузить Zigbee устройства');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: otkryvaem Zigbee pairing cherez backend MQTT publish.
export async function adminZigbeePermitJoin(seconds, token) {
  const body = seconds !== null && seconds !== undefined ? { seconds } : {};
  const response = await apiFetch('/api/admin/zigbee/permit-join', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Не удалось открыть сопряжение');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: otpravlyaem ON/OFF v Zigbee2MQTT topic ustroystva.
export async function adminZigbeeSetState(ieeeAddress, state, token) {
  const response = await apiFetch(`/api/admin/zigbee/devices/${encodeURIComponent(ieeeAddress)}/set-state`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ state }),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Не удалось отправить команду устройству');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}

// Translitem: pereimenovyvaem friendly_name ustroystva cherez Zigbee2MQTT request API.
export async function adminZigbeeRenameDevice(ieeeAddress, friendlyName, token) {
  const response = await apiFetch(`/api/admin/zigbee/devices/${encodeURIComponent(ieeeAddress)}/rename`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ friendly_name: friendlyName }),
  });
  if (!response.ok) {
    const message = await readErrorDetail(response, 'Не удалось переименовать устройство');
    throw new Error(message || DEFAULT_ERROR_MESSAGE);
  }
  return response.json();
}
