import { apiFetch, normalizeApiErrorMessage } from './client';

async function request(path, body) {
  const response = await apiFetch(path, body === undefined ? {} : {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(normalizeApiErrorMessage(data.detail || data.message, { status: response.status }));
  return data;
}

export const fetchPushokAvailability = () => request('/api/zigbee/pushok/availability');
export const connectPushok = (input) => request('/api/zigbee/pushok', input);
export const retryPushokPairing = (id) => request(`/api/zigbee/pushok/${encodeURIComponent(id)}/pair`, {});
export const fetchPushokConnection = (id) => request(`/api/zigbee/coordinators/${encodeURIComponent(id)}`);
