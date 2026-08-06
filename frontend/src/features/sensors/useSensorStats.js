import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchSensorHistory } from '../../api/sensors';
import { fetchPlantHistory } from '../../api/plants';
import {
  fetchAdminPumpHistory,
  fetchAdminResourceStatistics,
  fetchAdminZigbeeHistory,
} from '../../api/admin';
import { fetchResourceStatistics, fetchZigbeeHistory } from '../../api/selfService';
import { isSessionExpiredError } from '../../api/client';
import {
  addUiCalendarDaysMs,
  formatDateDDMM,
  formatDateKeyYYYYMMDD,
  parseBackendTimestamp,
  startOfUiDayMs,
} from '../../utils/formatters';
import { useAuth } from '../auth/AuthContext';
import { translateApp } from '../../locales/i18n';

const DAY_MS = 24 * 60 * 60 * 1000;
const DAILY_SUMMARY_DAYS = 7;

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

function toChartPoint(point, value, extra = {}) {
  const timestamp = point.ts ?? point.timestamp;
  const parsed = parseBackendTimestamp(timestamp);
  return {
    timestamp,
    timeMs: parsed ? parsed.getTime() : null,
    value,
    ...extra,
  };
}

function sortChartPoints(points) {
  return points
    .filter((point) => Number.isFinite(point.timeMs))
    .sort((a, b) => a.timeMs - b.timeMs);
}

function buildDailyOnDurations(points, nowMs) {
  if (!Array.isArray(points) || points.length === 0 || !Number.isFinite(nowMs)) {
    return [];
  }

  const sorted = sortChartPoints(points);
  if (sorted.length === 0) {
    return [];
  }

  const endWindow = nowMs;
  const startToday = startOfUiDayMs(nowMs);
  const startWindow = addUiCalendarDaysMs(startToday, -(DAILY_SUMMARY_DAYS - 1));
  const days = new Map();

  const ensureDay = (timeMs) => {
    const dayStartMs = startOfUiDayMs(timeMs);
    if (dayStartMs < startWindow || dayStartMs > startToday) {
      return null;
    }
    const dateKey = formatDateKeyYYYYMMDD(dayStartMs);
    if (!dateKey) {
      return null;
    }
    if (!days.has(dateKey)) {
      days.set(dateKey, {
        dateKey,
        dateLabel: formatDateDDMM(dayStartMs),
        dayStartMs,
        durationMs: 0,
      });
    }
    return days.get(dateKey);
  };

  const addInterval = (fromMs, toMs, isOn) => {
    const intervalStart = Math.max(fromMs, startWindow);
    const intervalEnd = Math.min(toMs, endWindow);
    if (intervalEnd <= intervalStart) {
      return;
    }

    let cursor = intervalStart;
    while (cursor < intervalEnd) {
      const dayStartMs = startOfUiDayMs(cursor);
      const nextDayMs = addUiCalendarDaysMs(dayStartMs, 1);
      const segmentEnd = Math.min(intervalEnd, nextDayMs);
      const day = ensureDay(cursor);
      if (day && isOn) {
        day.durationMs += segmentEnd - cursor;
      }
      cursor = segmentEnd;
    }
  };

  sorted.forEach((point, index) => {
    if (point.timeMs > endWindow || point.timeMs < startWindow - DAY_MS) {
      return;
    }
    ensureDay(point.timeMs);
    const nextPoint = sorted[index + 1];
    const intervalEnd = nextPoint?.timeMs ?? endWindow;
    addInterval(point.timeMs, intervalEnd, Number(point.value) >= 0.5);
  });

  return Array.from(days.values())
    .filter((day) => day.dayStartMs >= startWindow && day.dayStartMs <= startToday)
    .sort((a, b) => b.dayStartMs - a.dayStartMs)
    .map(({ dateKey, dateLabel, durationMs }) => ({ dateKey, dateLabel, durationMs }));
}

function localDateLabel(dateKey) {
  const match = String(dateKey || '').match(/^\d{4}-(\d{2})-(\d{2})$/);
  return match ? `${match[2]}.${match[1]}` : String(dateKey || '');
}

/**
 * Translitem: upravlyaet zagruzkoy istorii sensorov i metrik rastenij po diapazonam.
 */
export function useSensorStats({
  mode,
  sensorId,
  plantId,
  metric,
  pumpId,
  equipmentResourceId,
  equipmentStatsScope,
  zigbeeCoordinatorId,
  zigbeeIeeeAddress,
  zigbeeProperty,
  zigbeeHistoryScope,
  chartKind,
}) {
  const { token } = useAuth();
  const [activeRange, setActiveRange] = useState('day');
  const [dataByRange, setDataByRange] = useState({});
  const [isLoading, setIsLoading] = useState(false);
  const [isDailyLoading, setIsDailyLoading] = useState(false);
  const [error, setError] = useState(null);
  const inFlightKeysRef = useRef(new Set());
  const needsDailyOnDurations = mode === 'pump' || chartKind === 'binary';

  useEffect(() => {
    setActiveRange('day');
    setDataByRange({});
    setError(null);
    setIsLoading(false);
    setIsDailyLoading(false);
    inFlightKeysRef.current = new Set();
  }, [
    mode,
    sensorId,
    plantId,
    metric,
    pumpId,
    equipmentResourceId,
    equipmentStatsScope,
    zigbeeCoordinatorId,
    zigbeeIeeeAddress,
    zigbeeProperty,
    zigbeeHistoryScope,
    chartKind,
  ]);

  const loadRange = useCallback(
    async (range, options = {}) => {
      const { showLoading = true, showDailyLoading = false } = options;
      const hours = RANGE_TO_HOURS[range] ?? RANGE_TO_HOURS.day;
      const hasSensorTarget = mode === 'sensor' && sensorId;
      const hasPlantTarget = mode === 'plant' && plantId && metric;
      const hasZigbeeTarget = mode === 'zigbee' && zigbeeIeeeAddress && zigbeeProperty;
      const hasPumpTarget = mode === 'pump' && pumpId;
      const hasEquipmentTarget = mode === 'equipment' && equipmentResourceId;
      const targetKey = [
        mode || '',
        sensorId || '',
        plantId || '',
        metric || '',
        pumpId || '',
        equipmentResourceId || '',
        equipmentStatsScope || '',
        zigbeeCoordinatorId || '',
        zigbeeIeeeAddress || '',
        zigbeeProperty || '',
        zigbeeHistoryScope || '',
        range,
      ].join(':');
      if (
        (!hasSensorTarget && !hasPlantTarget && !hasZigbeeTarget && !hasPumpTarget && !hasEquipmentTarget)
        || dataByRange[range]
        || inFlightKeysRef.current.has(targetKey)
      ) {
        return;
      }

      inFlightKeysRef.current.add(targetKey);
      if (showLoading) {
        setIsLoading(true);
      }
      if (showDailyLoading) {
        setIsDailyLoading(true);
      }
      if (showLoading) {
        setError(null);
      }

      try {
        const loadedAtMs = Date.now();
        if (hasSensorTarget) {
          const sensorHistory = await fetchSensorHistory(sensorId, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { sensorHistory, loadedAtMs },
          }));
        } else if (hasPlantTarget) {
          const metricType = PLANT_METRIC_MAP[metric];
          const plantHistory = await fetchPlantHistory(plantId, hours, metricType, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { plantHistory, loadedAtMs },
          }));
        } else if (hasZigbeeTarget) {
          const zigbeeHistory = zigbeeHistoryScope === 'self-service'
            ? await fetchZigbeeHistory(
              zigbeeCoordinatorId,
              zigbeeIeeeAddress,
              zigbeeProperty,
              hours,
            )
            : await (
              zigbeeCoordinatorId
                ? fetchAdminZigbeeHistory(
                  zigbeeIeeeAddress,
                  zigbeeProperty,
                  hours,
                  token,
                  zigbeeCoordinatorId,
                )
                : fetchAdminZigbeeHistory(
                  zigbeeIeeeAddress,
                  zigbeeProperty,
                  hours,
                  token,
                )
            );
          setDataByRange((prev) => ({
            ...prev,
            [range]: { zigbeeHistory, loadedAtMs },
          }));
        } else if (hasPumpTarget) {
          const pumpHistory = await fetchAdminPumpHistory(pumpId, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { pumpHistory, loadedAtMs },
          }));
        } else if (hasEquipmentTarget) {
          const equipmentStatistics = equipmentStatsScope === 'self-service'
            ? await fetchResourceStatistics(equipmentResourceId, hours)
            : await fetchAdminResourceStatistics(equipmentResourceId, hours, token);
          setDataByRange((prev) => ({
            ...prev,
            [range]: { equipmentStatistics, loadedAtMs },
          }));
        }
      } catch (err) {
        if (isSessionExpiredError(err)) return;
        if (showLoading) {
          setError(err?.message || translateApp("Не удалось загрузить историю"));
        }
      } finally {
        inFlightKeysRef.current.delete(targetKey);
        if (showLoading) {
          setIsLoading(false);
        }
        if (showDailyLoading) {
          setIsDailyLoading(false);
        }
      }
    },
    [
      dataByRange,
      equipmentResourceId,
      equipmentStatsScope,
      metric,
      mode,
      plantId,
      pumpId,
      sensorId,
      token,
      zigbeeCoordinatorId,
      zigbeeHistoryScope,
      zigbeeIeeeAddress,
      zigbeeProperty,
    ],
  );

  useEffect(() => {
    if (!dataByRange[activeRange]) {
      loadRange(activeRange, { showLoading: true });
    }
  }, [activeRange, dataByRange, loadRange]);

  useEffect(() => {
    if (needsDailyOnDurations && activeRange !== 'week' && !dataByRange.week) {
      loadRange('week', { showLoading: false, showDailyLoading: true });
    }
  }, [activeRange, dataByRange.week, loadRange, needsDailyOnDurations]);

  const buildChartData = useCallback((rangeData) => {
    if (!rangeData) {
      return [];
    }

    if (mode === 'sensor') {
      const history = Array.isArray(rangeData.sensorHistory) ? rangeData.sensorHistory : [];
      return sortChartPoints(history.map((point) => toChartPoint(point, point.value)));
    }

    if (mode === 'plant') {
      const history = Array.isArray(rangeData.plantHistory) ? rangeData.plantHistory : [];
      const metricType = PLANT_METRIC_MAP[metric];
      const points = history
        .filter((point) => {
          if (!metricType) return true;
          const raw = point.metric_type ?? point.metricType;
          return raw === metricType;
        })
        .map((point) => toChartPoint(point, point.value));
      return sortChartPoints(points);
    }

    if (mode === 'zigbee') {
      const history = Array.isArray(rangeData.zigbeeHistory) ? rangeData.zigbeeHistory : [];
      return sortChartPoints(history.map((point) => toChartPoint(point, point.value, {
        rawValue: point.raw_value || point.rawValue || point.value_text || point.valueText,
      })));
    }

    if (mode === 'pump') {
      const history = Array.isArray(rangeData.pumpHistory) ? rangeData.pumpHistory : [];
      return sortChartPoints(history.map((point) => toChartPoint(
        point,
        point.value ?? (point.is_running === true ? 1 : point.is_running === false ? 0 : null),
        { rawValue: point.raw_status || point.rawStatus },
      )));
    }

    if (mode === 'equipment') {
      const statistics = rangeData.equipmentStatistics;
      const history = Array.isArray(statistics?.points) ? statistics.points : [];
      return sortChartPoints(history.map((point) => toChartPoint(
        point,
        point.value,
        { rawValue: point.raw_value || point.rawValue },
      )));
    }

    return [];
  }, [metric, mode]);

  const chartData = useMemo(() => buildChartData(dataByRange[activeRange]), [activeRange, buildChartData, dataByRange]);

  const dailyOnDurations = useMemo(() => {
    if (mode === 'equipment') {
      const statistics = dataByRange[activeRange]?.equipmentStatistics;
      const daily = Array.isArray(statistics?.daily) ? statistics.daily : [];
      return daily.map((item) => ({
        dateKey: item.date,
        dateLabel: localDateLabel(item.date),
        durationMs: item.on_duration_seconds === null || item.on_duration_seconds === undefined
          ? null
          : Number(item.on_duration_seconds) * 1000,
        energyKwh: item.energy_kwh ?? null,
        partial: Boolean(item.partial),
      }));
    }
    if (!needsDailyOnDurations) {
      return [];
    }
    const weekData = dataByRange.week;
    const points = buildChartData(weekData);
    return buildDailyOnDurations(points, weekData?.loadedAtMs);
  }, [activeRange, buildChartData, dataByRange, mode, needsDailyOnDurations]);

  const equipmentStatistics = mode === 'equipment'
    ? dataByRange[activeRange]?.equipmentStatistics
    : null;

  const setRange = useCallback((range) => {
    setActiveRange(range);
  }, []);

  return {
    activeRange,
    setRange,
    chartData,
    dailyOnDurations,
    responseChartKind: equipmentStatistics?.chart_kind || null,
    chartUnit: equipmentStatistics?.chart_unit || null,
    energySupported: Boolean(equipmentStatistics?.energy_supported),
    isLoading,
    isDailyLoading: isDailyLoading || (needsDailyOnDurations && activeRange === 'week' && isLoading),
    error,
  };
}
