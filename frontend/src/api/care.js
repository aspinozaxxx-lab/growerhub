import { apiFetch, readApiErrorMessage } from './client';

async function requestJson(url, init) {
  const response = await apiFetch(url, init);
  if (!response.ok) throw new Error(await readApiErrorMessage(response));
  return response.json().catch(() => null);
}

export const careRequest = (path, method = 'GET', body) => requestJson(`/api/care${path}`, {
  method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
});
export const telegramRequest = (path = '', method = 'GET', body) => requestJson(`/api/notifications/telegram${path}`, {
  method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
});
export const startCare = () => requestJson('/api/onboarding/care', { method: 'POST' });
export async function uploadCarePhoto(entryId, file) {
  const body = new FormData(); body.append('file', file, 'plant.jpg');
  const response = await apiFetch(`/api/care/entries/${entryId}/photos`, { method: 'POST', body });
  if (!response.ok) throw new Error(await readApiErrorMessage(response));
  return response.json();
}
