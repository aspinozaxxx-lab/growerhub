import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const SensorStatsContext = createContext(undefined);

function SensorStatsProvider({ children }) {
  const [state, setState] = useState({
    isOpen: false,
    mode: null,
    sensorId: null,
    plantId: null,
    metric: null,
    title: null,
    subtitle: null,
  });

  const openSensorStats = useCallback((payload) => {
    if (!payload) {
      return;
    }
    const {
      mode,
      sensorId,
      plantId,
      metric,
      title,
      subtitle,
    } = payload;

    if (mode === 'sensor') {
      if (!sensorId) {
        return;
      }
      setState({
        isOpen: true,
        mode,
        sensorId,
        plantId: null,
        metric: metric || null,
        title: title || null,
        subtitle: subtitle || null,
      });
      return;
    }

    if (mode === 'plant') {
      if (!plantId || !metric) {
        return;
      }
      setState({
        isOpen: true,
        mode,
        sensorId: null,
        plantId,
        metric,
        title: title || null,
        subtitle: subtitle || null,
      });
    }
  }, []);

  const closeSensorStats = useCallback(() => {
    setState((prev) => ({
      ...prev,
      isOpen: false,
    }));
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      openSensorStats,
      closeSensorStats,
    }),
    [closeSensorStats, openSensorStats, state],
  );

  return (
    <SensorStatsContext.Provider value={value}>
      {children}
    </SensorStatsContext.Provider>
  );
}

function useSensorStatsContext() {
  const ctx = useContext(SensorStatsContext);
  if (ctx === undefined) {
    throw new Error('useSensorStatsContext must be used within a SensorStatsProvider');
  }
  return ctx;
}

export { SensorStatsProvider, useSensorStatsContext };
