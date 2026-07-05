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
import { formatDateDDMM, formatSensorValue, formatTimeHHMM, formatTimestampLabel } from '../../utils/formatters';
import SidePanel from '../../components/ui/SidePanel';
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
  device_state: 'Состояние устройства',
  pump: 'Состояние полива',
};

function formatAxisLabel(timestamp, range) {
  if (range === 'hour' || range === 'day') {
    return formatTimeHHMM(timestamp);
  }
  return formatDateDDMM(timestamp);
}

function formatDurationMs(durationMs) {
  const totalMinutes = Math.floor(Math.max(0, durationMs) / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours > 0 && minutes > 0) {
    return `${hours} ч ${minutes} мин`;
  }
  if (hours > 0) {
    return `${hours} ч`;
  }
  if (totalMinutes > 0) {
    return `${minutes} мин`;
  }
  return '0 мин';
}

function WateringTooltip({ active, payload, label }) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  const data = payload[0]?.payload || {};
  const volume = data.value;

  return (
    <div className="recharts-default-tooltip">
      <p className="recharts-tooltip-label">{formatTimestampLabel(label)}</p>
      {volume !== undefined && volume !== null && (
        <p className="recharts-tooltip-item">{`Объём: ${formatSensorValue(volume)} л`}</p>
      )}
    </div>
  );
}

function BinaryTooltip({ active, payload, label, onLabel, offLabel, valueLabel }) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  const data = payload[0]?.payload || {};
  const value = Number(data.value);
  const labelText = value >= 0.5 ? onLabel : offLabel;

  return (
    <div className="recharts-default-tooltip">
      <p className="recharts-tooltip-label">{formatTimestampLabel(label)}</p>
      <p className="recharts-tooltip-item">{`${valueLabel}: ${labelText}`}</p>
      {data.rawValue && <p className="recharts-tooltip-item">{`Исходное значение: ${data.rawValue}`}</p>}
    </div>
  );
}

function DailyOnSummary({ items, isLoading }) {
  return (
    <section className="sensor-sidebar__daily" aria-label="Включено по дням">
      <div className="sensor-sidebar__daily-header">
        <h3>Включено по дням</h3>
        <span>Последние 7 дней</span>
      </div>
      {isLoading ? (
        <div className="sensor-sidebar__daily-state">Загрузка списка...</div>
      ) : items.length === 0 ? (
        <div className="sensor-sidebar__daily-state">Нет данных за последние 7 дней</div>
      ) : (
        <ul className="sensor-sidebar__daily-list">
          {items.map((item) => (
            <li key={item.dateKey} className="sensor-sidebar__daily-item">
              <span>{item.dateLabel}</span>
              <strong>{formatDurationMs(item.durationMs)}</strong>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SensorChart({
  metric,
  range,
  data,
  chartKind = 'numeric',
  valueLabel,
  binaryOnLabel = 'Включено',
  binaryOffLabel = 'Выключено',
}) {
  const preparedData = Array.isArray(data)
    ? data.filter((item) => (
      item?.timestamp
      && Number.isFinite(item.timeMs)
      && item.value !== null
      && item.value !== undefined
    ))
    : [];
  const empty = preparedData.length === 0;

  if (empty) {
    return <div className="sensor-chart__empty">Нет данных для выбранного периода</div>;
  }

  if (metric === 'watering') {
    const prepared = preparedData.map((item, idx) => ({
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

  if (chartKind === 'binary') {
    const label = valueLabel || METRIC_LABELS[metric] || 'Состояние';
    return (
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={preparedData} margin={{ top: 8, right: 8, left: -12, bottom: 8 }}>
          <CartesianGrid strokeDasharray="4 4" stroke="rgba(255,255,255,0.08)" />
          <XAxis
            dataKey="timeMs"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(value) => formatAxisLabel(value, range)}
            tick={{ fill: '#c7d7ef', fontSize: 12 }}
          />
          <YAxis
            domain={[0, 1]}
            ticks={[0, 1]}
            tickFormatter={(value) => (Number(value) >= 0.5 ? binaryOnLabel : binaryOffLabel)}
            tick={{ fill: '#c7d7ef', fontSize: 12 }}
            width={86}
          />
          <Tooltip
            content={(
              <BinaryTooltip
                onLabel={binaryOnLabel}
                offLabel={binaryOffLabel}
                valueLabel={label}
              />
            )}
          />
          <Line
            type="stepAfter"
            dataKey="value"
            stroke="#6bdba8"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={preparedData} margin={{ top: 8, right: 8, left: -12, bottom: 8 }}>
        <CartesianGrid strokeDasharray="4 4" stroke="rgba(255,255,255,0.08)" />
        <XAxis
          dataKey="timeMs"
          type="number"
          scale="time"
          domain={['dataMin', 'dataMax']}
          tickFormatter={(value) => formatAxisLabel(value, range)}
          tick={{ fill: '#c7d7ef', fontSize: 12 }}
        />
        <YAxis tick={{ fill: '#c7d7ef', fontSize: 12 }} />
        <Tooltip
          formatter={(value) => [formatSensorValue(value), valueLabel || METRIC_LABELS[metric] || metric]}
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
  const {
    isOpen,
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
    closeSensorStats,
  } = useSensorStatsContext();

  const shouldLoad = isOpen && (
    (mode === 'sensor' && sensorId)
    || (mode === 'plant' && plantId && metric)
    || (mode === 'zigbee' && zigbeeIeeeAddress && zigbeeProperty)
    || (mode === 'pump' && pumpId)
  );
  const resolvedChartKind = chartKind || (mode === 'pump' ? 'binary' : 'numeric');
  const {
    activeRange,
    setRange,
    chartData,
    dailyOnDurations,
    isLoading,
    isDailyLoading,
    error,
  } = useSensorStats({
    mode: shouldLoad ? mode : null,
    sensorId: shouldLoad ? sensorId : null,
    plantId: shouldLoad ? plantId : null,
    pumpId: shouldLoad ? pumpId : null,
    zigbeeIeeeAddress: shouldLoad ? zigbeeIeeeAddress : null,
    zigbeeProperty: shouldLoad ? zigbeeProperty : null,
    metric: shouldLoad ? metric : null,
    chartKind: shouldLoad ? resolvedChartKind : null,
  });
  const showDailyOnSummary = resolvedChartKind === 'binary' || mode === 'pump';

  const fallbackTitle = useMemo(() => {
    const metricLabel = metric ? METRIC_LABELS[metric] || metric : 'История';
    return metricLabel;
  }, [metric]);

  if (!isOpen) {
    return null;
  }

  return (
    <SidePanel
      isOpen={isOpen}
      onClose={closeSensorStats}
      title={title || fallbackTitle}
      subtitle={subtitle || ''}
    >
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
          <SensorChart
            metric={metric}
            range={activeRange}
            data={chartData}
            chartKind={resolvedChartKind}
            valueLabel={valueLabel}
            binaryOnLabel={binaryOnLabel || 'Включено'}
            binaryOffLabel={binaryOffLabel || 'Выключено'}
          />
        )}
      </div>

      {showDailyOnSummary && (
        <DailyOnSummary items={dailyOnDurations} isLoading={isDailyLoading} />
      )}
    </SidePanel>
  );
}

export default SensorStatsSidebar;
