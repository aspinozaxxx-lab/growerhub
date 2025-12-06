import React, { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useSensorStats } from './useSensorStats';
import { useSensorStatsContext } from './SensorStatsContext';
import { formatSensorValue, formatTimestampLabel } from '../../utils/formatters';
import './SensorStatsSidebar.css';

const RANGE_OPTIONS = [
  { key: 'hour', label: 'За час' },
  { key: 'day', label: 'За сутки' },
  { key: 'week', label: 'За неделю' },
  { key: 'month', label: 'За месяц' },
];

const METRIC_LABELS = {
  air_temperature: 'Температура воздуха',
  air_humidity: 'Влажность воздуха',
  soil_moisture: 'Влажность почвы',
  watering: 'Поливы',
};

function formatAxisLabel(timestamp, range) {
  const date = new Date(timestamp);
  const hours = `${date.getHours()}`.padStart(2, '0');
  const minutes = `${date.getMinutes()}`.padStart(2, '0');
  if (range === 'hour' || range === 'day') {
    return `${hours}:${minutes}`;
  }
  const day = `${date.getDate()}`.padStart(2, '0');
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  return `${day}.${month}`;
}

function WateringTooltip({ active, payload, label }) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  const data = payload[0]?.payload || {};
  const volume = data.water_used ?? data.value;
  const duration = data.duration;
  const ph = data.ph;
  const fertilizers = data.fertilizers_per_liter;

  return (
    <div className="recharts-default-tooltip">
      <p className="recharts-tooltip-label">{formatTimestampLabel(label)}</p>
      {volume !== undefined && volume !== null && (
        <p className="recharts-tooltip-item">{`Объём: ${formatSensorValue(volume)} л`}</p>
      )}
      {duration !== undefined && duration !== null && (
        <p className="recharts-tooltip-item">{`Длительность: ${duration} c`}</p>
      )}
      {ph !== undefined && ph !== null && <p className="recharts-tooltip-item">{`pH: ${ph}`}</p>}
      {fertilizers ? (
        <p className="recharts-tooltip-item">{`Удобрения: ${
          typeof fertilizers === 'string' ? fertilizers : JSON.stringify(fertilizers)
        }`}</p>
      ) : null}
    </div>
  );
}

function SensorChart({ metric, range, data }) {
  const empty = !data || data.length === 0;

  if (empty) {
    return <div className="sensor-chart__empty">Нет данных для выбранного периода</div>;
  }

  if (metric === 'watering') {
    const prepared = data.map((item, idx) => ({
      ...item,
      id: idx,
    }));
    return (
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={prepared} margin={{ top: 8, right: 8, left: -12, bottom: 8 }}>
          <CartesianGrid strokeDasharray="4 4" stroke="rgba(255,255,255,0.08)" />
          <XAxis
            dataKey="timestamp"
            tickFormatter={(value) => formatAxisLabel(value, range)}
            tick={{ fill: '#c7d7ef', fontSize: 12 }}
          />
          <YAxis
            tick={{ fill: '#c7d7ef', fontSize: 12 }}
            label={{
              value: 'Объём полива (л)',
              angle: -90,
              position: 'insideLeft',
              fill: '#c7d7ef',
              fontSize: 12,
            }}
          />
          <Tooltip content={<WateringTooltip />} />
          <Bar dataKey="value" fill="#6bdba8" radius={[6, 6, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 8 }}>
        <CartesianGrid strokeDasharray="4 4" stroke="rgba(255,255,255,0.08)" />
        <XAxis
          dataKey="timestamp"
          tickFormatter={(value) => formatAxisLabel(value, range)}
          tick={{ fill: '#c7d7ef', fontSize: 12 }}
        />
        <YAxis tick={{ fill: '#c7d7ef', fontSize: 12 }} />
        <Tooltip
          formatter={(value) => [formatSensorValue(value), METRIC_LABELS[metric] || metric]}
          labelFormatter={(value) => formatTimestampLabel(value)}
          contentStyle={{ fontSize: '0.9rem' }}
          labelStyle={{ color: '#0f172a', fontWeight: 600 }}
        />
        <Line
          type="monotone"
          dataKey="value"
          stroke="#6bdba8"
          strokeWidth={2}
          dot={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

function SensorStatsSidebar() {
  const { isOpen, deviceId, deviceName, metric, closeSensorStats } = useSensorStatsContext();
  const shouldLoad = isOpen && deviceId && metric;
  const { activeRange, setRange, chartData, isLoading, error } = useSensorStats(
    shouldLoad ? deviceId : null,
    shouldLoad ? metric : null,
  );

  const title = useMemo(() => {
    const metricLabel = metric ? METRIC_LABELS[metric] || metric : '';
    const deviceLabel = deviceName || deviceId || '';
    return `${metricLabel}${deviceLabel ? ` — ${deviceLabel}` : ''}`;
  }, [deviceId, deviceName, metric]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className={`sensor-sidebar ${isOpen ? 'is-open' : ''}`}>
      <button className="sensor-sidebar__backdrop" type="button" onClick={closeSensorStats} aria-label="Закрыть" />
      <aside className="sensor-sidebar__panel" aria-label="Статистика датчиков">
        <header className="sensor-sidebar__header">
          <div>
            <div className="sensor-sidebar__metric">{title}</div>
            {deviceName && <div className="sensor-sidebar__device">{deviceId}</div>}
          </div>
          <button
            type="button"
            className="sensor-sidebar__close"
            onClick={closeSensorStats}
            aria-label="Закрыть панель статистики"
          >
            ×
          </button>
        </header>

        <div className="sensor-sidebar__ranges" role="group" aria-label="Диапазон">
          {RANGE_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              className={`sensor-sidebar__range-btn ${activeRange === option.key ? 'is-active' : ''}`}
              onClick={() => setRange(option.key)}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="sensor-sidebar__chart">
          {isLoading ? (
            <div className="sensor-sidebar__state">Загрузка...</div>
          ) : error ? (
            <div className="sensor-sidebar__state sensor-sidebar__state--error">{error}</div>
          ) : (
            <SensorChart metric={metric} range={activeRange} data={chartData} />
          )}
        </div>
      </aside>
    </div>
  );
}

export default SensorStatsSidebar;
