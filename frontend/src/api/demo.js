import { apiFetch, readApiErrorMessage } from './client';

const jsonRequest = (body) => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
export const startDemoSession = (locale, timezone) => apiFetch('/api/demo/start', jsonRequest({ locale, timezone }));
export const saveDemoSession = (replace) => apiFetch('/api/demo/save', { ...jsonRequest({ replace }), authScope: 'account' });
export const resetDemoSession = () => apiFetch('/api/demo/reset', { method: 'POST', authScope: 'demo' });
export const refreshDemoSession = (accountToken) => fetch('/api/demo/refresh', {
  method: 'POST', credentials: 'same-origin', headers: accountToken ? { Authorization: `Bearer ${accountToken}` } : {},
});

async function data(path, body) {
  const response = await apiFetch('/api/demo/' + path, body ? jsonRequest(body) : {});
  if (!response.ok) throw new Error(await readApiErrorMessage(response));
  return response.json();
}
export const fetchDemoStatus = () => data('status');
export const fetchDemoCatalog = () => data('catalog');
export const addDemoDevice = (body) => data('devices', body);
export const changeDemoEnvironment = (body) => data('environment', body);
