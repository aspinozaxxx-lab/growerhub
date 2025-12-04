import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchSensorHistory, fetchWateringLogs } from '../../api/history';
import { useAuth } from '../auth/AuthContext';

const SENSOR_RANGE_TO_HOURS = {
  hour: 1,
  day: 24,
  week: 168,
  month: 720,
};

const WATERING_RANGE_TO_DAYS = {
  hour: 1, // Используем сутки и фильтруем на фронте, чтобы не ломать UI.
  day: 1,
  week: 7,
  month: 30,
};

/**
 * Управляет загрузкой и кэшированием истории датчиков/поливов по диапазонам.
 */
export function useSensorStats(deviceId, metric) {
  const { token } = useAuth();
  const [activeRange, setActiveRange] = useState('day');
  const [dataByRange, setDataByRange] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // Сброс состояния при смене устройства или метрики.
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

  // Загружаем данные для активного диапазона при первом запросе.
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
            const tsCandidate = new Date(log.start_time || log.startTime || log.timestamp || log.time).getTime();
            return Number.isFinite(tsCandidate) ? tsCandidate >= cutoffMs : true;
          }
          return true;
        })
        .map((log) => {
          const raw = log.start_time || log.startTime || log.timestamp || log.time;
          const tsValue = raw ? new Date(raw) : new Date();
          const safeDate = Number.isFinite(tsValue.getTime()) ? tsValue : new Date();
          return {
            timestamp: safeDate.toISOString(),
            value: log.duration ?? log.water_used ?? 1, // простой столбик по duration/объёму
            duration: log.duration,
            water_used: log.water_used,
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
