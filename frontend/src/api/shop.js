import { apiFetch } from './client';

async function request(url, { method = 'GET', payload, signal } = {}) {
  const response = await apiFetch(url, {
    method,
    authScope: 'account',
    signal,
    cache: 'no-store',
    headers: { Accept: 'application/json', ...(payload ? { 'Content-Type': 'application/json' } : {}) },
    ...(payload ? { body: JSON.stringify(payload) } : {}),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error('Shop request failed');
    error.status = response.status;
    error.code = data?.code || 'REQUEST_FAILED';
    throw error;
  }
  return data;
}

export const fetchShopCatalog = (signal) => request('/api/shop/catalog', { signal });
export const createShopRequest = (payload) => request('/api/shop/requests', { method: 'POST', payload });
export const fetchAdminShopRequests = ({ page = 0, size = 20, status = '' } = {}) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return request(`/api/admin/shop/requests?${params}`);
};
export const fetchAdminShopRequest = (id) => request(`/api/admin/shop/requests/${encodeURIComponent(id)}`);
export const updateAdminShopRequest = (id, status) => request(`/api/admin/shop/requests/${encodeURIComponent(id)}`, { method: 'PATCH', payload: { status } });
export const retryShopNotification = (id) => request(`/api/admin/shop/requests/${encodeURIComponent(id)}/retry-notification`, { method: 'POST' });
