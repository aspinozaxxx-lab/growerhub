import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchPumpWateringStatus, stopPumpWatering } from '../../api/pumps';
import { isSessionExpiredError } from '../../api/client';
import { useWateringSidebar } from './WateringSidebarContext';

function parseStatus(payload) {
  const raw = payload?.status ?? payload?.state ?? null;
  if (!raw) {
    return null;
  }
  return String(raw).toLowerCase();
}

function parseRemainingSeconds(payload) {
  const raw = payload?.remaining_s ?? payload?.remaining ?? payload?.remainingSeconds ?? null;
  if (raw === null || raw === undefined) {
    return null;
  }
  const numeric = Number(raw);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  return Math.max(0, Math.floor(numeric));
}

function parseDurationSeconds(payload) {
  const raw = payload?.duration_s ?? payload?.duration ?? payload?.durationSeconds ?? null;
  if (raw === null || raw === undefined) {
    return null;
  }
  const numeric = Number(raw);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  return Math.max(0, Math.floor(numeric));
}

function normalizeStatusSnapshot(payload) {
  if (!payload) {
    return null;
  }
  return {
    status: parseStatus(payload),
    remainingS: parseRemainingSeconds(payload),
    durationS: parseDurationSeconds(payload),
    startedAt: payload?.startedAt ?? payload?.started_at ?? payload?.startTime ?? payload?.start_time ?? null,
    updatedAt: payload?.updatedAt ?? payload?.updated_at ?? new Date().toISOString(),
  };
}

function buildDeadlineMs(remainingSeconds) {
  if (remainingSeconds === null || remainingSeconds === undefined) {
    return null;
  }
  return Date.now() + remainingSeconds * 1000;
}

function getRemainingFromDeadline(deadlineMs) {
  if (!deadlineMs) {
    return null;
  }
  return Math.max(0, Math.ceil((deadlineMs - Date.now()) / 1000));
}

function usePumpWateringStatus(pumpId, { enabled = true } = {}) {
  const { wateringByPump, setWateringStatus, requestWateringRefresh } = useWateringSidebar();
  const localSnapshot = pumpId ? wateringByPump[pumpId] ?? null : null;
  const normalizedLocalSnapshot = useMemo(() => normalizeStatusSnapshot(localSnapshot), [localSnapshot]);

  const [status, setStatus] = useState(() => normalizedLocalSnapshot?.status ?? null);
  const [remainingSeconds, setRemainingSeconds] = useState(() => normalizedLocalSnapshot?.remainingS ?? null);
  const [deadlineMs, setDeadlineMs] = useState(() => buildDeadlineMs(normalizedLocalSnapshot?.remainingS ?? null));
  const [isLoading, setIsLoading] = useState(false);
  const requestIdRef = useRef(0);
  const intervalRef = useRef(null);
  const zeroCheckRequestedRef = useRef(false);

  const syncSnapshot = useCallback((snapshot) => {
    const normalized = normalizeStatusSnapshot(snapshot);
    const nextStatus = normalized?.status ?? null;
    const nextRemaining = normalized?.remainingS ?? null;
    setStatus(nextStatus);
    setRemainingSeconds(nextRemaining);
    setDeadlineMs(buildDeadlineMs(nextRemaining));
    if (nextRemaining === null || nextRemaining > 0) {
      zeroCheckRequestedRef.current = false;
    }
    return normalized;
  }, []);

  const applyServerSnapshot = useCallback((snapshot) => {
    const normalized = syncSnapshot(snapshot);
    if (!pumpId) {
      return normalized;
    }
    if (normalized?.status === 'running') {
      setWateringStatus(pumpId, normalized);
      return normalized;
    }
    setWateringStatus(pumpId, null);
    requestWateringRefresh();
    return normalized;
  }, [pumpId, requestWateringRefresh, setWateringStatus, syncSnapshot]);

  const fetchStatus = useCallback(async () => {
    if (!pumpId) {
      setStatus(null);
      setRemainingSeconds(null);
      setDeadlineMs(null);
      return null;
    }
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const payload = await fetchPumpWateringStatus(pumpId);
      if (requestId !== requestIdRef.current) {
        return null;
      }
      return applyServerSnapshot(payload);
    } catch (err) {
      if (requestId !== requestIdRef.current) {
        return null;
      }
      if (isSessionExpiredError(err)) {
        return null;
      }
      return null;
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [applyServerSnapshot, pumpId]);

  const shouldTrack = Boolean(
    pumpId && (
      enabled
      || normalizedLocalSnapshot?.status === 'running'
      || status === 'running'
    ),
  );

  useEffect(() => {
    if (normalizedLocalSnapshot?.status !== 'running') {
      return;
    }
    syncSnapshot(normalizedLocalSnapshot);
  }, [normalizedLocalSnapshot, syncSnapshot]);

  useEffect(() => {
    if (!shouldTrack) {
      return;
    }
    fetchStatus();
  }, [fetchStatus, shouldTrack]);

  useEffect(() => {
    if (status !== 'running') {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return undefined;
    }
    if (!deadlineMs) {
      return undefined;
    }

    const syncRemaining = () => {
      setRemainingSeconds(getRemainingFromDeadline(deadlineMs));
    };

    syncRemaining();
    intervalRef.current = setInterval(syncRemaining, 1000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [deadlineMs, status]);

  useEffect(() => {
    if (!pumpId || status !== 'running') {
      return undefined;
    }

    const handleFocus = () => {
      fetchStatus();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchStatus();
      }
    };

    window.addEventListener('focus', handleFocus);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.removeEventListener('focus', handleFocus);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [fetchStatus, pumpId, status]);

  useEffect(() => {
    if (!pumpId || status !== 'running') {
      return undefined;
    }

    // Redkij refetch nuzhen, chtoby UI ne zastrjeval na 0 bez push-kanala s servera.
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'hidden') {
        return;
      }
      fetchStatus();
    }, 10000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [fetchStatus, pumpId, status]);

  useEffect(() => {
    if (status !== 'running' || remainingSeconds === null || remainingSeconds > 0) {
      if (remainingSeconds === null || remainingSeconds > 0) {
        zeroCheckRequestedRef.current = false;
      }
      return;
    }
    if (zeroCheckRequestedRef.current) {
      return;
    }
    zeroCheckRequestedRef.current = true;
    fetchStatus();
  }, [fetchStatus, remainingSeconds, status]);

  const stop = useCallback(async () => {
    if (!pumpId) {
      return;
    }
    await stopPumpWatering(pumpId);
    setWateringStatus(pumpId, null);
    setStatus('idle');
    setRemainingSeconds(0);
    setDeadlineMs(null);
    const snapshot = await fetchStatus();
    if (!snapshot) {
      requestWateringRefresh();
    }
  }, [fetchStatus, pumpId, requestWateringRefresh, setWateringStatus]);

  const resolvedStatus = status ?? normalizedLocalSnapshot?.status ?? null;
  const resolvedRemainingSeconds = remainingSeconds ?? normalizedLocalSnapshot?.remainingS ?? null;

  const isRunning = useMemo(() => {
    if (resolvedStatus === null || resolvedStatus === undefined) {
      return null;
    }
    return resolvedStatus === 'running';
  }, [resolvedStatus]);

  return {
    status: resolvedStatus,
    isRunning,
    remainingSeconds: resolvedRemainingSeconds,
    isLoading,
    refetch: fetchStatus,
    stop,
  };
}

export default usePumpWateringStatus;
