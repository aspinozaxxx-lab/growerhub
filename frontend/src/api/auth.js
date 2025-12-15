// Translitem: API helpery dlya raboty so sposobami vhoda i paroljem.
import { apiFetch } from './client';

/**
 * Translitem: vozvrashchaet status dostupnyh sposobov vhoda tekushchego polzovatelya.
 */
export async function fetchAuthMethods(token) {
  void token;
  const response = await apiFetch('/api/auth/methods');
  if (!response.ok) {
    throw new Error(`Ne udalos zagruzit sposoby vhoda (${response.status})`);
  }
  return response.json();
}

/**
 * Translitem: nachinaet link SSO (Google/Yandex) i vozvrashchaet URL provajdera.
 */
export async function linkSsoMethod(provider, redirectPath, token) {
  void token;
  const headers = { Accept: 'application/json' };
  const url = `/api/auth/sso/${encodeURIComponent(provider)}/login?redirect_path=${encodeURIComponent(redirectPath)}`;
  const response = await apiFetch(url, { headers });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.detail || `Ne udalos nachat privyazku (${response.status})`);
  }
  const data = await response.json();
  if (!data?.url) {
    throw new Error('Ne poluchen url dlya SSO');
  }
  return data.url;
}

/**
 * Translitem: udalyaet ukazannyj sposob vhoda (local/google/yandex).
 */
export async function unlinkAuthMethod(provider, token) {
  void token;
  const response = await apiFetch(`/api/auth/methods/${encodeURIComponent(provider)}`, {
    method: 'DELETE',
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || `Ne udalos udalit's sposob vhoda (${response.status})`);
  }
  return data;
}

/**
 * Translitem: vklyuchaet ili obnovlyaet lokal'nyj login/parol'.
 */
export async function setLocalLogin(email, password, token) {
  void token;
  const headers = {
    'Content-Type': 'application/json',
  };
  const response = await apiFetch('/api/auth/methods/local', {
    method: 'POST',
    headers,
    body: JSON.stringify({ email, password }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || `Ne udalos sohranit lokal'nyj vhod (${response.status})`);
  }
  return data;
}

/**
 * Translitem: menyajet parol' dlya lokal'nogo vhoda.
 */
export async function changePassword(currentPassword, newPassword, token) {
  void token;
  const headers = {
    'Content-Type': 'application/json',
  };
  const response = await apiFetch('/api/auth/change-password', {
    method: 'POST',
    headers,
    body: JSON.stringify({
      current_password: currentPassword,
      new_password: newPassword,
    }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || `Ne udalos izmenit parol' (${response.status})`);
  }
  return data;
}
