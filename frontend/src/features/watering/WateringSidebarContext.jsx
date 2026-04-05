import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const WateringSidebarContext = createContext(undefined);

function parseStatus(status) {
  if (!status) {
    return null;
  }
  return String(status).toLowerCase();
}

function parseInteger(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  return Math.max(0, Math.floor(numeric));
}

function normalizeRunningStatus(status) {
  if (!status) {
    return null;
  }
  const normalizedStatus = parseStatus(status.status ?? status.state);
  if (normalizedStatus !== 'running') {
    return null;
  }
  return {
    status: normalizedStatus,
    durationS: parseInteger(status.durationS ?? status.duration_s ?? status.duration),
    startedAt: status.startedAt ?? status.started_at ?? status.startTime ?? status.start_time ?? null,
    remainingS: parseInteger(status.remainingS ?? status.remaining_s ?? status.remaining ?? status.remainingSeconds),
    updatedAt: status.updatedAt ?? status.updated_at ?? new Date().toISOString(),
  };
}

function WateringSidebarProvider({ children }) {
  const [state, setState] = useState({
    isOpen: false,
    pumpId: null,
    pumpLabel: null,
    plantId: null,
    wateringAdvice: null,
    wateringPrevious: null,
  });
  const [wateringByPump, setWateringByPump] = useState({});
  const [refreshVersion, setRefreshVersion] = useState(0);

  const openWateringSidebar = useCallback(({ pumpId, pumpLabel, plantId, wateringAdvice, wateringPrevious }) => {
    if (!pumpId) {
      return;
    }
    setState({
      isOpen: true,
      pumpId,
      pumpLabel: pumpLabel || null,
      plantId: plantId || null,
      wateringAdvice: wateringAdvice || null,
      wateringPrevious: wateringPrevious || null,
    });
  }, []);

  const closeWateringSidebar = useCallback(() => {
    setState((prev) => ({
      ...prev,
      isOpen: false,
    }));
  }, []);

  const setWateringStatus = useCallback((pumpId, status) => {
    if (!pumpId) return;
    setWateringByPump((prev) => {
      const normalized = normalizeRunningStatus(status);
      if (!normalized) {
        if (!prev[pumpId]) return prev;
        const next = { ...prev };
        delete next[pumpId];
        return next;
      }
      return {
        ...prev,
        [pumpId]: normalized,
      };
    });
  }, []);

  const requestWateringRefresh = useCallback(() => {
    setRefreshVersion((prev) => prev + 1);
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      wateringByPump,
      refreshVersion,
      openWateringSidebar,
      closeWateringSidebar,
      setWateringStatus,
      requestWateringRefresh,
    }),
    [
      closeWateringSidebar,
      openWateringSidebar,
      refreshVersion,
      requestWateringRefresh,
      setWateringStatus,
      state,
      wateringByPump,
    ],
  );

  return (
    <WateringSidebarContext.Provider value={value}>
      {children}
    </WateringSidebarContext.Provider>
  );
}

function useWateringSidebar() {
  const ctx = useContext(WateringSidebarContext);
  if (ctx === undefined) {
    throw new Error('useWateringSidebar must be used within a WateringSidebarProvider');
  }
  return ctx;
}

export { WateringSidebarProvider, useWateringSidebar };
