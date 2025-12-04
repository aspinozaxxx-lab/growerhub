import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const SensorStatsContext = createContext(undefined);

function SensorStatsProvider({ children }) {
  const [state, setState] = useState({
    isOpen: false,
    deviceId: null,
    deviceName: null,
    metric: null,
  });

  const openSensorStats = useCallback(({ deviceId, metric, deviceName }) => {
    if (!deviceId || !metric) {
      return;
    }
    setState({
      isOpen: true,
      deviceId,
      deviceName: deviceName || null,
      metric,
    });
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
