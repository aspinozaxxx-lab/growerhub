import { apiFetch, normalizeApiErrorMessage } from './client';

async function request(url, method = 'GET', payload) {
  const response = await apiFetch(url, {
    method,
    headers: { Accept: 'application/json', ...(payload ? { 'Content-Type': 'application/json' } : {}) },
    ...(payload ? { body: JSON.stringify(payload) } : {}),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(normalizeApiErrorMessage(data.detail, { status: response.status }));
  return data;
}

export const fetchPushokPilot = () => request('/api/users/me/pushok-pilot');
export const savePushokPilot = (payload) => request('/api/users/me/pushok-pilot', 'PUT', payload);
export const withdrawPushokPilot = () => request('/api/users/me/pushok-pilot', 'DELETE');
export const fetchAdminPushokPilots = () => request('/api/admin/pushok-pilots');
export const markPushokPilotContacted = (userId) => request(`/api/admin/pushok-pilots/${encodeURIComponent(userId)}/contacted`, 'POST');
