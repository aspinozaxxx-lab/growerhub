import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchSensorHistory, fetchWateringLogs } from '../../api/history';
import { useAuth } from '../auth/AuthContext';
import { parseBackendTimestamp } from '../../utils/formatters';

const SENSOR_RANGE_TO_HOURS = {
  hour: 1,
  day: 24,
  week: 168,
  month: 720,
};

const WATERING_RANGE_TO_DAYS = {
  hour: 1, // Translitem: ispol'zuem sutki i fil'truem na fronte, chtoby ne l'omat' UI.
  day: 1,
  week: 7,
  month: 30,
};

/**
 * Translitem: upravlyaet zagruzkoy i keshirovaniem istorii datchikov/polivov po diapazonam.
 */
export function useSensorStats(deviceId, metric) {
  const { token } = useAuth();
  const [activeRange, setActiveRange] = useState('day');
  const [dataByRange, setDataByRange] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // Translitem: sbros sostoyaniya pri smene ustroystva ili metriki.
  useEffect(() => {
    setActiveRange('day');
    setDataByRange({});
    setError(null);
    setIsLoading(false);
  }, [deviceId, metric]);

  const loadRange = useCallback(
    async (range) => {
      if (!deviceId || dataByRange[range]) {
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const hours = SENSOR_RANGE_TO_HOURS[range] ?? SENSOR_RANGE_TO_HOURS.day;
        const days = WATERING_RANGE_TO_DAYS[range] ?? WATERING_RANGE_TO_DAYS.day;

        const [sensorHistory, wateringLogs] = await Promise.all([
          fetchSensorHistory(deviceId, hours, token),
          fetchWateringLogs(deviceId, days, token),
        ]);

        setDataByRange((prev) => ({
          ...prev,
          [range]: { sensorHistory, wateringLogs },
        }));
      } catch (err) {
        setError(err?.message || 'Не удалось загрузить статистику');
      } finally {
        setIsLoading(false);
      }
    },
    [dataByRange, deviceId, token],
  );

  // Translitem: zagruzhaem dannye dlya aktivnogo diapazona pri pervom zaprose.
  useEffect(() => {
    if (!deviceId) {
      return;
    }
    if (!dataByRange[activeRange]) {
      loadRange(activeRange);
    }
  }, [activeRange, dataByRange, deviceId, loadRange]);

  const chartData = useMemo(() => {
    const rangeData = dataByRange[activeRange];
    if (!rangeData) {
      return [];
    }

    if (metric === 'watering') {
      const now = Date.now();
      const rangeHours = SENSOR_RANGE_TO_HOURS[activeRange] ?? SENSOR_RANGE_TO_HOURS.day;
      const cutoffMs = now - rangeHours * 60 * 60 * 1000;
      const wateringLogs = Array.isArray(rangeData.wateringLogs) ? rangeData.wateringLogs : [];

      return wateringLogs
        .filter((log) => {
          if (activeRange === 'hour') {
            const raw = log.start_time || log.startTime || log.timestamp || log.time;
            const tsCandidate = parseBackendTimestamp(raw)?.getTime();
            return Number.isFinite(tsCandidate) ? tsCandidate >= cutoffMs : true;
          }
          return true;
        })
        .map((log) => {
          const raw = log.start_time || log.startTime || log.timestamp || log.time;
          const parsed = raw ? parseBackendTimestamp(raw) : null;
          const safeDate = parsed || new Date();
          return {
            timestamp: safeDate.toISOString(),
            value: log.water_used ?? log.duration ?? 1, // Translitem: fallback: water_used -> duration -> 1.
            duration: log.duration,
            water_used: log.water_used,
            ph: log.ph,
            fertilizers_per_liter: log.fertilizers_per_liter ?? log.fertilizers,
          };
        });
    }

    const history = Array.isArray(rangeData.sensorHistory) ? rangeData.sensorHistory : [];
    return history
      .map((point) => ({
        timestamp: point.timestamp,
        value: point?.[metric],
      }))
      .filter((item) => item.value !== undefined && item.value !== null);
  }, [activeRange, dataByRange, metric]);

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
