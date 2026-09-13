import i18n, { getCurrentLocale, translateApp } from '../locales/i18n';

// Translitem: edinyj wrapper dlya fetch s avtomaticheskim refresh i retry pri 401.

const SESSION_EXPIRED_CODE = 'SESSION_EXPIRED';
const CYRILLIC_PATTERN = /[А-Яа-яЁё]/;

const STATUS_ERROR_KEYS = {
  400: 'Проверьте введённые данные',
  401: 'Необходимо войти в аккаунт',
  403: 'Недостаточно прав для этого действия',
  404: 'Запрошенные данные не найдены',
  409: 'Действие конфликтует с текущим состоянием',
  429: 'Слишком много запросов, повторите попытку позже',
  502: 'Внешний сервис временно недоступен',
  503: 'Сервис временно недоступен',
};

const getStatusErrorMessage = (status) => {
  const key = STATUS_ERROR_KEYS[status];
  return key ? translateApp(key) : null;
};

const getSafeFallback = (fallback) => {
  if (typeof fallback !== 'string' || !fallback.trim()) return null;
  const normalized = fallback.trim();
  if (getCurrentLocale() === 'ru') {
    return CYRILLIC_PATTERN.test(normalized) ? normalized : null;
  }
  if (!CYRILLIC_PATTERN.test(normalized)) return normalized;
  return i18n.exists(normalized, { ns: 'app', lng: 'en' })
    ? translateApp(normalized)
    : null;
};

export function normalizeApiErrorMessage(message, { status, fallback } = {}) {
  const normalized = typeof message === 'string' ? message.trim() : '';
  const safeFallback = getSafeFallback(fallback);
  const statusMessage = getStatusErrorMessage(status);
  if (!normalized) {
    return safeFallback || statusMessage || translateApp('Не удалось выполнить запрос');
  }
  if (getCurrentLocale() === 'ru') {
    return CYRILLIC_PATTERN.test(normalized)
      ? normalized
      : safeFallback || statusMessage || translateApp('Не удалось выполнить запрос');
  }
  if (
    CYRILLIC_PATTERN.test(normalized)
    && i18n.exists(normalized, { ns: 'app', lng: 'en' })
  ) {
    return translateApp(normalized);
  }
  return safeFallback || statusMessage || translateApp('Не удалось выполнить запрос');
}

export async function readApiErrorMessage(response, fallback) {
  const data = await response.json().catch(() => ({}));
  return normalizeApiErrorMessage(data.detail || data.message, {
    status: response.status,
    fallback,
  });
}

class SessionExpiredError extends Error {
  constructor() {
    super(SESSION_EXPIRED_CODE);
    this.name = 'SessionExpiredError';
    this.code = SESSION_EXPIRED_CODE;
  }
}

let handlers = {};
let requestGeneration = 0;
let sessionController = new AbortController();
const refreshPromises = new Map();

export function registerAuthHandlers(next = {}) {
  handlers = next;
}

export function getAuthMode() {
  return handlers.getMode?.() || 'account';
}

export function resetApiSession() {
  requestGeneration += 1;
  sessionController.abort();
  sessionController = new AbortController();
  refreshPromises.clear();
}

export function isSessionExpiredError(err) {
  return err?.code === SESSION_EXPIRED_CODE || err?.message === SESSION_EXPIRED_CODE || err === SESSION_EXPIRED_CODE;
}

function requestScope(url, explicitScope) {
  if (explicitScope) return explicitScope;
  if (/^\/api\/auth\//u.test(url) || /^\/api\/demo\/(start|refresh|save|logout)$/u.test(url)) return 'account';
  return getAuthMode();
}

async function refreshed(scope) {
  if (!refreshPromises.has(scope)) {
    const pending = Promise.resolve().then(() => handlers.refresh?.(scope)).then((token) => {
      if (!token) throw new SessionExpiredError();
      return token;
    }).finally(() => {
      if (refreshPromises.get(scope) === pending) refreshPromises.delete(scope);
    });
    refreshPromises.set(scope, pending);
  }
  return refreshPromises.get(scope);
}

export async function apiFetch(url, init = {}) {
  const { authScope, ...fetchInit } = init;
  const scope = requestScope(url, authScope);
  const generation = requestGeneration;
  const signal = init.signal ? AbortSignal.any([init.signal, sessionController.signal]) : sessionController.signal;
  const requireCurrent = () => {
    if (generation !== requestGeneration || signal.aborted) throw new DOMException('Session changed', 'AbortError');
  };
  const send = async (token) => {
    requireCurrent();
    const headers = new Headers(init.headers || {});
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(url, { ...fetchInit, headers, signal, credentials: init.credentials ?? 'same-origin' });
    requireCurrent();
    return response;
  };
  let response = await send(handlers.getToken?.(scope));
  if (response.status === 401 && !/\/(?:auth|demo)\/(?:refresh|logout)$/u.test(url)) {
    try {
      const token = await refreshed(scope);
      requireCurrent();
      response = await send(token);
      if (response.status === 401) throw new SessionExpiredError();
    } catch (error) {
      requireCurrent();
      handlers.expire?.(scope);
      throw isSessionExpiredError(error) ? error : new SessionExpiredError();
    }
  }
  if (scope === 'demo' && response.ok && !['GET', 'HEAD'].includes((init.method || 'GET').toUpperCase())) {
    handlers.onDemoAction?.(url, init.method);
  }
  return response;
}
