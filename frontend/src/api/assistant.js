import { apiFetch, readApiErrorMessage } from './client';
import { translateApp as t } from '../locales/i18n';

async function checked(response, fallback) {
  if (response.ok) return response;
  const error = new Error(await readApiErrorMessage(response, fallback));
  error.retryAfter = Number(response.headers.get('Retry-After')) || 0;
  throw error;
}
export async function assistantRequest(path, method = 'GET', body) {
  const response = await checked(await apiFetch(`/api/advisor${path}`, { method,
    ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) }));
  return response.json();
}
export async function uploadAssistantPhoto(chat, file) {
  const body = new FormData(); body.append('photo', file);
  return (await checked(await apiFetch(`/api/advisor/conversations/${chat}/photos`, { method: 'POST', body }))).json();
}
export async function assistantPhoto(id) {
  return (await checked(await apiFetch(`/api/advisor/photos/${id}`))).blob();
}
export async function watchAssistant(id, onMessage, signal) {
  const response = await checked(await apiFetch(`/api/advisor/messages/${id}/stream`, { signal, headers: { Accept: 'text/event-stream' } }));
  const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read(); if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let end;
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2);
        const data = frame.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5)).join('\n');
        if (data) onMessage(JSON.parse(data));
      }
    }
  } finally { reader.releaseLock(); }
}
export async function ruleRequest(path = '', method = 'GET', body) {
  const response = await apiFetch(`/api/automation/rules${path}`, { method,
    ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) });
  return (await checked(response, response.status === 422 ? t('Проверьте настройки сценария, готовность устройств и другие сценарии для них.') : undefined)).json();
}
