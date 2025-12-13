import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const WateringSidebarContext = createContext(undefined);

function WateringSidebarProvider({ children }) {
  const [state, setState] = useState({
    isOpen: false,
    deviceId: null,
    plantId: null,
  });
  const [wateringByDevice, setWateringByDevice] = useState({});

  const openWateringSidebar = useCallback(({ deviceId, plantId }) => {
    if (!deviceId) {
      return;
    }
    setState({
      isOpen: true,
      deviceId,
      plantId: plantId || null,
    });
  }, []);

  const closeWateringSidebar = useCallback(() => {
    setState((prev) => ({
      ...prev,
      isOpen: false,
    }));
  }, []);

  const setWateringStatus = useCallback((deviceId, status) => {
    if (!deviceId) return;
    setWateringByDevice((prev) => {
      if (!status) {
        // Translitem: ochistka statusa esli poliv ne aktivnyj.
        if (!prev[deviceId]) return prev;
        const next = { ...prev };
        delete next[deviceId];
        return next;
      }
      return {
        ...prev,
        [deviceId]: status,
      };
    });
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      wateringByDevice,
      openWateringSidebar,
      closeWateringSidebar,
      setWateringStatus,
    }),
    [closeWateringSidebar, openWateringSidebar, setWateringStatus, state, wateringByDevice],
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

