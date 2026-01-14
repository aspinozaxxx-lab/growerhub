import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const WateringSidebarContext = createContext(undefined);

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
      if (!status) {
        if (!prev[pumpId]) return prev;
        const next = { ...prev };
        delete next[pumpId];
        return next;
      }
      return {
        ...prev,
        [pumpId]: status,
      };
    });
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      wateringByPump,
      openWateringSidebar,
      closeWateringSidebar,
      setWateringStatus,
    }),
    [closeWateringSidebar, openWateringSidebar, setWateringStatus, state, wateringByPump],
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
