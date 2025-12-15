// Translitem: edinyj wrapper dlya fetch s avtomaticheskim refresh i retry pri 401.

const ACCESS_TOKEN_STORAGE_KEY = 'gh_access_token';
const SESSION_EXPIRED_CODE = 'SESSION_EXPIRED';

class SessionExpiredError extends Error {
  constructor() {
    super(SESSION_EXPIRED_CODE);
    this.name = 'SessionExpiredError';
    this.code = SESSION_EXPIRED_CODE;
  }
}

let logoutHandler = null;
let tokenGetter = null;
let refreshPromise = null;

export function registerAuthHandlers({ logout, getToken } = {}) {
  // Translitem: logout - callback iz AuthContext; getToken - opcionalno dlya chteniya tokina iz state.
  logoutHandler = typeof logout === 'function' ? logout : null;
  tokenGetter = typeof getToken === 'function' ? getToken : null;
}

export function isSessionExpiredError(err) {
  return (
    err?.code === SESSION_EXPIRED_CODE ||
    err?.message === SESSION_EXPIRED_CODE ||
    err === SESSION_EXPIRED_CODE
  );
}

function getAccessToken() {
  try {
    const fromStorage = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    if (fromStorage) return fromStorage;
  } catch {
    // ignore
  }
  const fromHandler = tokenGetter ? tokenGetter() : null;
  return fromHandler || null;
}

function setAccessToken(token) {
  try {
    if (!token) {
      localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
      return;
    }
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
  } catch {
    // ignore
  }
}

async function triggerLogout() {
  // Translitem: v ideal'e logout iz AuthContext; fallback - ochistka localStorage.
  if (logoutHandler) {
    logoutHandler();
    return;
  }
  setAccessToken(null);
}

async function doRefreshAccessToken() {
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new SessionExpiredError();
  }

  const payload = await response.json().catch(() => ({}));
  const accessToken = payload?.access_token;
  if (!accessToken) {
    throw new SessionExpiredError();
  }
  setAccessToken(accessToken);
  return accessToken;
}

async function ensureRefreshed() {
  if (!refreshPromise) {
    refreshPromise = doRefreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function shouldSkipAutoRefresh(url) {
  if (typeof url !== 'string') return false;
  return url === '/api/auth/refresh' || url === '/api/auth/logout';
}

export async function apiFetch(url, init = {}) {
  const headers = new Headers(init.headers || {});
  const token = getAccessToken();
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const requestInit = {
    ...init,
    headers,
    credentials: init.credentials ?? 'same-origin',
  };

  const response = await fetch(url, requestInit);
  if (response.status !== 401 || shouldSkipAutoRefresh(url)) {
    return response;
  }

  try {
    await ensureRefreshed();
  } catch (err) {
    await triggerLogout();
    throw isSessionExpiredError(err) ? err : new SessionExpiredError();
  }

  const retryHeaders = new Headers(init.headers || {});
  const refreshedToken = getAccessToken();
  if (refreshedToken && !retryHeaders.has('Authorization')) {
    retryHeaders.set('Authorization', `Bearer ${refreshedToken}`);
  }

  const retryResponse = await fetch(url, {
    ...init,
    headers: retryHeaders,
    credentials: requestInit.credentials,
  });

  if (retryResponse.status === 401) {
    await triggerLogout();
    throw new SessionExpiredError();
  }

  return retryResponse;
}
