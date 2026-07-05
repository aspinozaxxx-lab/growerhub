/* eslint-disable react-refresh/only-export-components -- Translitem: context eksportiruet provider i hook po sushchestvuyushchemu patternu. */
import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';

const SensorStatsContext = createContext(undefined);

function SensorStatsProvider({ children }) {
  const [state, setState] = useState({
    isOpen: false,
    mode: null,
    sensorId: null,
    plantId: null,
    pumpId: null,
    zigbeeIeeeAddress: null,
    zigbeeProperty: null,
    metric: null,
    chartKind: null,
    valueLabel: null,
    binaryOnLabel: null,
    binaryOffLabel: null,
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
      pumpId,
      zigbeeIeeeAddress,
      zigbeeProperty,
      metric,
      chartKind,
      valueLabel,
      binaryOnLabel,
      binaryOffLabel,
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
        pumpId: null,
        zigbeeIeeeAddress: null,
        zigbeeProperty: null,
        metric: metric || null,
        chartKind: chartKind || null,
        valueLabel: valueLabel || null,
        binaryOnLabel: binaryOnLabel || null,
        binaryOffLabel: binaryOffLabel || null,
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
        pumpId: null,
        zigbeeIeeeAddress: null,
        zigbeeProperty: null,
        metric,
        chartKind: chartKind || null,
        valueLabel: valueLabel || null,
        binaryOnLabel: binaryOnLabel || null,
        binaryOffLabel: binaryOffLabel || null,
        title: title || null,
        subtitle: subtitle || null,
      });
      return;
    }

    if (mode === 'zigbee') {
      if (!zigbeeIeeeAddress || !zigbeeProperty) {
        return;
      }
      setState({
        isOpen: true,
        mode,
        sensorId: null,
        plantId: null,
        pumpId: null,
        zigbeeIeeeAddress,
        zigbeeProperty,
        metric: metric || null,
        chartKind: chartKind || null,
        valueLabel: valueLabel || null,
        binaryOnLabel: binaryOnLabel || null,
        binaryOffLabel: binaryOffLabel || null,
        title: title || null,
        subtitle: subtitle || null,
      });
      return;
    }

    if (mode === 'pump') {
      if (!pumpId) {
        return;
      }
      setState({
        isOpen: true,
        mode,
        sensorId: null,
        plantId: null,
        pumpId,
        zigbeeIeeeAddress: null,
        zigbeeProperty: null,
        metric: metric || 'pump',
        chartKind: chartKind || 'binary',
        valueLabel: valueLabel || null,
        binaryOnLabel: binaryOnLabel || null,
        binaryOffLabel: binaryOffLabel || null,
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
