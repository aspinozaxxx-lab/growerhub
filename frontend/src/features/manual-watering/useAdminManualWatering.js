import { useCallback, useEffect, useRef, useState } from 'react';
import {
  fetchAdminManualWateringOverview,
  fetchAdminManualWateringSessions,
  startAdminManualWatering,
  stopAdminManualWatering,
} from '../../api/admin';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../auth/AuthContext';
import {
  normalizeManualWateringOverview,
  normalizeSessionPage,
  overviewHasActiveSession,
  mergeSessionsById,
} from './manualWateringModel';
import { translateApp } from '../../locales/i18n';

const ACTIVE_POLL_INTERVAL_MS = 3000;
const IDLE_POLL_INTERVAL_MS = 15000;
const SESSION_PAGE_SIZE = 10;

function emptyHistoryState() {
  return {
    items: [],
    nextBeforeId: null,
    isLoading: false,
    error: '',
    loaded: false,
    hasLoadedMore: false,
  };
}

export default function useAdminManualWatering() {
  const { token } = useAuth();
  const [overview, setOverview] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [actionKey, setActionKey] = useState('');
  const [histories, setHistories] = useState({});
  const activePumpIdsRef = useRef(new Set());
  const pendingHistoryRefreshRef = useRef(new Set());
  const overviewRequestIdRef = useRef(0);

  const loadOverview = useCallback(async ({ silent = false } = {}) => {
    const requestId = overviewRequestIdRef.current + 1;
    overviewRequestIdRef.current = requestId;
    if (!silent) setIsLoading(true);
    try {
      const data = await fetchAdminManualWateringOverview(token);
      if (requestId !== overviewRequestIdRef.current) return null;
      setOverview(normalizeManualWateringOverview(data));
      setError('');
      return data;
    } catch (err) {
      if (requestId !== overviewRequestIdRef.current) return null;
      if (isSessionExpiredError(err)) return null;
      setError(err?.message || translateApp("Не удалось загрузить ручной полив"));
      return null;
    } finally {
      if (requestId === overviewRequestIdRef.current) setIsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  const hasActiveSession = overviewHasActiveSession(overview);

  useEffect(() => {
    const pollInterval = hasActiveSession
      ? ACTIVE_POLL_INTERVAL_MS
      : IDLE_POLL_INTERVAL_MS;
    const poll = () => {
      if (document.visibilityState === 'visible') {
        loadOverview({ silent: true });
      }
    };
    const intervalId = window.setInterval(poll, pollInterval);
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') poll();
    };
    const handleFocus = () => poll();
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleFocus);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, [hasActiveSession, loadOverview]);

  const loadSessions = useCallback(async (pumpId, { append = false, refresh = false } = {}) => {
    const current = histories[pumpId] || emptyHistoryState();
    if (current.isLoading || (append && !current.nextBeforeId)) return;
    setHistories((prev) => ({
      ...prev,
      [pumpId]: { ...(prev[pumpId] || emptyHistoryState()), isLoading: true, error: '' },
    }));
    try {
      const data = await fetchAdminManualWateringSessions(pumpId, {
        limit: SESSION_PAGE_SIZE,
        beforeId: append ? current.nextBeforeId : null,
      }, token);
      const page = normalizeSessionPage(data);
      setHistories((prev) => {
        const previous = prev[pumpId] || emptyHistoryState();
        return {
          ...prev,
          [pumpId]: {
            items: append
              ? mergeSessionsById(previous.items, page.items)
              : refresh
                ? mergeSessionsById(page.items, previous.items)
                : page.items,
            nextBeforeId: refresh && previous.hasLoadedMore
              ? previous.nextBeforeId
              : page.nextBeforeId,
            isLoading: false,
            error: '',
            loaded: true,
            hasLoadedMore: append ? true : refresh ? previous.hasLoadedMore : false,
          },
        };
      });
    } catch (err) {
      if (isSessionExpiredError(err)) return;
      setHistories((prev) => ({
        ...prev,
        [pumpId]: {
          ...(prev[pumpId] || emptyHistoryState()),
          isLoading: false,
          error: err?.message || translateApp("Не удалось загрузить журнал насоса"),
        },
      }));
    }
  }, [histories, token]);

  useEffect(() => {
    const nextActiveIds = new Set(
      (Array.isArray(overview?.pumps) ? overview.pumps : [])
        .filter((pump) => Boolean(pump?.current_session || pump?.active_session))
        .map((pump) => pump.id),
    );
    activePumpIdsRef.current.forEach((pumpId) => {
      if (!nextActiveIds.has(pumpId) && histories[pumpId]?.loaded) {
        pendingHistoryRefreshRef.current.add(pumpId);
      }
    });
    activePumpIdsRef.current = nextActiveIds;
    pendingHistoryRefreshRef.current.forEach((pumpId) => {
      const history = histories[pumpId];
      if (history?.loaded && !history.isLoading) {
        pendingHistoryRefreshRef.current.delete(pumpId);
        loadSessions(pumpId, { refresh: true });
      }
    });
  }, [histories, loadSessions, overview]);

  const refreshLoadedHistory = useCallback(async (pumpId) => {
    if (histories[pumpId]?.loaded) {
      await loadSessions(pumpId, { refresh: true });
    }
  }, [histories, loadSessions]);

  const clearActionError = useCallback(() => setActionError(''), []);

  const startWatering = useCallback(async (pumpId, payload) => {
    if (actionKey) return false;
    setActionKey(`start:${pumpId}`);
    setError('');
    setActionError('');
    setNotice('');
    try {
      await startAdminManualWatering(pumpId, payload, token);
      await loadOverview({ silent: true });
      await refreshLoadedHistory(pumpId);
      setNotice(translateApp("Полив запущен"));
      return true;
    } catch (err) {
      if (isSessionExpiredError(err)) return false;
      setActionError(err?.message || translateApp("Не удалось запустить полив"));
      return false;
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, refreshLoadedHistory, token]);

  const stopWatering = useCallback(async (pumpId) => {
    if (actionKey) return false;
    setActionKey(`stop:${pumpId}`);
    setError('');
    setActionError('');
    setNotice('');
    try {
      await stopAdminManualWatering(pumpId, token);
      await loadOverview({ silent: true });
      await refreshLoadedHistory(pumpId);
      setNotice(translateApp("Остановка полива запрошена"));
      return true;
    } catch (err) {
      if (isSessionExpiredError(err)) return false;
      setActionError(err?.message || translateApp("Не удалось остановить полив"));
      return false;
    } finally {
      setActionKey('');
    }
  }, [actionKey, loadOverview, refreshLoadedHistory, token]);

  return {
    overview,
    isLoading,
    error,
    actionError,
    notice,
    actionKey,
    histories,
    loadOverview,
    loadSessions,
    startWatering,
    stopWatering,
    clearActionError,
  };
}
