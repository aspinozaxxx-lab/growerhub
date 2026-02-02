import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchPumpWateringStatus, stopPumpWatering } from '../../api/pumps';
import { isSessionExpiredError } from '../../api/client';

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

function usePumpWateringStatus(pumpId, { enabled = true } = {}) {
  const [status, setStatus] = useState(null);
  const [serverRemainingSeconds, setServerRemainingSeconds] = useState(null);
  const [remainingSeconds, setRemainingSeconds] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const requestIdRef = useRef(0);
  const intervalRef = useRef(null);

  const fetchStatus = useCallback(async () => {
    if (!pumpId) {
      setStatus(null);
      setRemainingSeconds(null);
      return;
    }
    const requestId = ++requestIdRef.current;
    setIsLoading(true);
    try {
      const payload = await fetchPumpWateringStatus(pumpId);
      if (requestId !== requestIdRef.current) {
        return;
      }
      setStatus(parseStatus(payload));
      setServerRemainingSeconds(parseRemainingSeconds(payload));
    } catch (err) {
      if (isSessionExpiredError(err)) {
        return;
      }
      if (requestId !== requestIdRef.current) {
        return;
      }
      setStatus(null);
      setServerRemainingSeconds(null);
    } finally {
      if (requestId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [pumpId]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    fetchStatus();
  }, [enabled, fetchStatus]);

  useEffect(() => {
    if (serverRemainingSeconds === null || serverRemainingSeconds === undefined) {
      setRemainingSeconds(null);
      return;
    }
    const safe = Math.max(0, Math.floor(serverRemainingSeconds));
    setRemainingSeconds((prev) => {
      if (prev === null || prev === undefined) {
        return safe;
      }
      return prev > safe ? safe : prev;
    });
  }, [serverRemainingSeconds]);

  const stop = useCallback(async () => {
    if (!pumpId) {
      return;
    }
    await stopPumpWatering(pumpId);
    await fetchStatus();
  }, [pumpId, fetchStatus]);

  const isRunning = useMemo(() => {
    if (status === null || status === undefined) {
      return null;
    }
    return status === 'running';
  }, [status]);

  useEffect(() => {
    if (!isRunning || remainingSeconds === null || remainingSeconds === undefined || remainingSeconds <= 0) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return undefined;
    }
    if (intervalRef.current) {
      return undefined;
    }
    intervalRef.current = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev === null || prev === undefined) {
          return 0;
        }
        return Math.max(0, prev - 1);
      });
    }, 1000);
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isRunning, remainingSeconds]);

  return {
    status,
    isRunning,
    remainingSeconds,
    isLoading,
    refetch: fetchStatus,
    stop,
  };
}

export default usePumpWateringStatus;
