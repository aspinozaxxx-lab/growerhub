import { apiFetch, readApiErrorMessage } from './client';
import { translateApp as t } from '../locales/i18n';

async function controlRequest(path, method = 'GET', body, signal) {
  const response = await apiFetch(`/api/automation${path}`, {
    method, signal,
    ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
  });
  if (!response.ok) throw new Error(await readApiErrorMessage(response, t('Не удалось проверить управление устройством')));
  return response.json();
}

export const fetchResourceControls = signal => controlRequest('/controls', 'GET', undefined, signal);
export const setResourceControl = (resourceId, command) => controlRequest(`/resources/${resourceId}/control`, 'PUT', command);
export const releaseResourceControl = (resourceId, demand) => controlRequest(
  `/resources/${resourceId}/control?${new URLSearchParams({ demandId: demand.id, revision: demand.revision })}`, 'DELETE',
);
