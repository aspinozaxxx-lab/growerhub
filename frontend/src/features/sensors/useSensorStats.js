import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchSensorHistory } from '../../api/sensors';
import { fetchPlantHistory } from '../../api/plants';
import { fetchAdminPumpHistory, fetchAdminZigbeeHistory } from '../../api/admin';
import { isSessionExpiredError } from '../../api/client';
import { useAuth } from '../auth/AuthContext';

const RANGE_TO_HOURS = {
  hour: 1,
  day: 24,
  week: 168,
  month: 720,
};

const PLANT_METRIC_MAP = {
  air_temperature: 'AIR_TEMPERATURE',
  air_humidity: 'AIR_HUMIDITY',
  soil_moisture: 'SOIL_MOISTURE',
  watering: 'WATERING_VOLUME_L',
};

/**
 * Translitem: upravlyaet zagruzkoy istorii sensorov i metrik rastenij po diapazonam.
 */
export function useSensorStats({
  mode,
  sensorId,
  plantId,
  metric,
  pumpId,
  zigbeeIeeeAddress,
  zigbeeProperty,
}) {
  const { token } = useAuth();
  const [activeRange, setActiveRange] = useState('day');
  const [dataByRange, setDataByRange] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const inFlightKeyRef = useRef(null);

  useEffect(() => {
    setActiveRange('day');
    setDataByRange({});
    setError(null);
    setIsLoading(false);
    inFlightKeyRef.current = null;
  }, [mode, sensorId, plantId, metric, pumpId, zigbeeIeeeAddress, zigbeeProperty]);

  const loadRange = useCallback(
    async (range) => {
      const hours = RANGE_TO_HOURS[range] ?? RANGE_TO_HOURS.day;
      const hasSensorTarget = mode === 'sensor' && sensorId;
      const hasPlantTarget = mode === 'plant' && plantId && metric;
      const hasZigbeeTarget = mode === 'zigbee' && zigbeeIeeeAddress && zigbeeProperty;
      const hasPumpTarget = mode === 'pump' && pumpId;
      const targetKey = [
        mode || '',
        sensorId || '',
        plantId || '',
        metric || '',
        pumpId || '',
        zigbeeIeeeAddress || '',
        zigbeeProperty || '',
        range,
      ].join(':');
      if (
        (!hasSensorTarget && !hasPlantTarget && !hasZigbeeTarget && !hasPumpTarget)
        || dataByRange[range]
        || inFlightKeyRef.current === targetKey
      ) {
        return;
      }

      inFlightKeyRef.current = targetKey;
      setIsLoading(true);
      setError(null);

      try {
        if (hasSensorTarget) {
          const sensorHistory = await fetchSensorHistory(sensorId, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { sensorHistory },
          }));
        } else if (hasPlantTarget) {
          const metricType = PLANT_METRIC_MAP[metric];
          const plantHistory = await fetchPlantHistory(plantId, hours, metricType, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { plantHistory },
          }));
        } else if (hasZigbeeTarget) {
          const zigbeeHistory = await fetchAdminZigbeeHistory(zigbeeIeeeAddress, zigbeeProperty, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { zigbeeHistory },
          }));
        } else if (hasPumpTarget) {
          const pumpHistory = await fetchAdminPumpHistory(pumpId, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { pumpHistory },
          }));
        }
      } catch (err) {
        if (isSessionExpiredError(err)) return;
        setError(err?.message || 'Не удалось загрузить историю');
      } finally {
        if (inFlightKeyRef.current === targetKey) {
          inFlightKeyRef.current = null;
        }
        setIsLoading(false);
      }
    },
    [dataByRange, metric, mode, plantId, pumpId, sensorId, token, zigbeeIeeeAddress, zigbeeProperty],
  );

  useEffect(() => {
    if (!dataByRange[activeRange]) {
      loadRange(activeRange);
    }
  }, [activeRange, dataByRange, loadRange]);

  const chartData = useMemo(() => {
    const rangeData = dataByRange[activeRange];
    if (!rangeData) {
      return [];
    }

    if (mode === 'sensor') {
      const history = Array.isArray(rangeData.sensorHistory) ? rangeData.sensorHistory : [];
      return history.map((point) => ({
        timestamp: point.ts || point.timestamp,
        value: point.value,
      }));
    }

    if (mode === 'plant') {
      const history = Array.isArray(rangeData.plantHistory) ? rangeData.plantHistory : [];
      const metricType = PLANT_METRIC_MAP[metric];
      return history
        .filter((point) => {
          if (!metricType) return true;
          const raw = point.metric_type || point.metricType;
          return raw === metricType;
        })
        .map((point) => ({
          timestamp: point.ts || point.timestamp,
          value: point.value,
        }));
    }

    if (mode === 'zigbee') {
      const history = Array.isArray(rangeData.zigbeeHistory) ? rangeData.zigbeeHistory : [];
      return history.map((point) => ({
        timestamp: point.ts || point.timestamp,
        value: point.value,
        rawValue: point.raw_value || point.rawValue || point.value_text || point.valueText,
      }));
    }

    if (mode === 'pump') {
      const history = Array.isArray(rangeData.pumpHistory) ? rangeData.pumpHistory : [];
      return history.map((point) => ({
        timestamp: point.ts || point.timestamp,
        value: point.value ?? (point.is_running === true ? 1 : point.is_running === false ? 0 : null),
        rawValue: point.raw_status || point.rawStatus,
      }));
    }

    return [];
  }, [activeRange, dataByRange, metric, mode]);

  const setRange = useCallback((range) => {
    setActiveRange(range);
  }, []);

  return {
    activeRange,
    setRange,
    chartData,
    isLoading,
    error,
  };
}
