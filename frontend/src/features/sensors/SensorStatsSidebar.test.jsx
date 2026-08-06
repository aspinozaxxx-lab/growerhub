import React from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_UI_TIME_ZONE, setUiTimeZone } from '../../utils/formatters';
import SensorStatsSidebar from './SensorStatsSidebar';

const sensorStatsState = vi.hoisted(() => ({
  context: null,
  stats: null,
}));

vi.mock('./SensorStatsContext', () => ({
  useSensorStatsContext: () => sensorStatsState.context,
}));

vi.mock('./useSensorStats', () => ({
  useSensorStats: () => sensorStatsState.stats,
}));

vi.mock('../../components/ui/SidePanel', () => ({
  default: ({ isOpen, children }) => (isOpen ? children : null),
}));

vi.mock('recharts', async () => {
  const ReactModule = await vi.importActual('react');
  const ChartDataContext = ReactModule.createContext([]);

  function Chart({ data, children }) {
    return ReactModule.createElement(ChartDataContext.Provider, { value: data }, children);
  }

  function Tooltip({ content, formatter, labelFormatter }) {
    const data = ReactModule.useContext(ChartDataContext);
    const payload = [{ payload: data[0] }];
    const fallbackLabel = '2026-01-01T10:00:00Z';

    if (content) {
      return ReactModule.cloneElement(content, {
        active: true,
        payload,
        label: fallbackLabel,
      });
    }

    const formattedValue = formatter ? formatter(data[0]?.value)?.[0] : null;
    return ReactModule.createElement(
      ReactModule.Fragment,
      null,
      ReactModule.createElement(
        'p',
        { className: 'recharts-tooltip-label' },
        labelFormatter(fallbackLabel, payload),
      ),
      formattedValue
        ? ReactModule.createElement('p', { className: 'recharts-tooltip-value' }, formattedValue)
        : null,
    );
  }

  function XAxis({ dataKey, tickFormatter }) {
    const data = ReactModule.useContext(ChartDataContext);
    return ReactModule.createElement(
      'p',
      { className: 'recharts-axis-label' },
      tickFormatter(data[0][dataKey]),
    );
  }

  function Line({ type }) {
    return ReactModule.createElement('span', { 'data-testid': 'chart-line', 'data-type': type });
  }

  function YAxis({ label }) {
    return label?.value
      ? ReactModule.createElement('p', { className: 'recharts-y-axis-label' }, label.value)
      : null;
  }

  const Empty = () => null;
  const ResponsiveContainer = ({ children }) => children;

  return {
    Bar: Empty,
    BarChart: Chart,
    CartesianGrid: Empty,
    Line,
    LineChart: Chart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
  };
});

describe('SensorStatsSidebar', () => {
  beforeEach(() => {
    setUiTimeZone('Asia/Yekaterinburg');
    sensorStatsState.context = {
      isOpen: true,
      mode: 'sensor',
      sensorId: 7,
      plantId: null,
      pumpId: null,
      equipmentResourceId: null,
      equipmentStatsScope: null,
      zigbeeCoordinatorId: null,
      zigbeeIeeeAddress: null,
      zigbeeProperty: null,
      zigbeeHistoryScope: null,
      metric: 'air_temperature',
      chartKind: 'numeric',
      valueLabel: 'Температура',
      binaryOnLabel: 'Включено',
      binaryOffLabel: 'Выключено',
      title: 'Статистика',
      subtitle: '',
      closeSensorStats: vi.fn(),
    };
    sensorStatsState.stats = {
      activeRange: 'hour',
      setRange: vi.fn(),
      chartData: [{
        timestamp: '2026-01-01T00:00:00',
        timeMs: Date.parse('2026-01-01T00:00:00Z'),
        value: 23.5,
        rawValue: 'ON',
      }],
      dailyOnDurations: [],
      responseChartKind: null,
      chartUnit: null,
      energySupported: false,
      isLoading: false,
      isDailyLoading: false,
      error: '',
    };
  });

  afterEach(() => {
    cleanup();
    setUiTimeZone(DEFAULT_UI_TIME_ZONE);
  });

  it.each([
    ['chislovogo grafika', 'air_temperature', 'numeric'],
    ['binarnogo grafika', 'device_state', 'binary'],
    ['grafika polivov', 'watering', 'numeric'],
  ])('formatiruet tooltip %s v timezone polzovatelya', (_, metric, chartKind) => {
    sensorStatsState.context = {
      ...sensorStatsState.context,
      metric,
      chartKind,
    };

    const { container } = render(<SensorStatsSidebar />);

    expect(container.querySelector('.recharts-axis-label')).toHaveTextContent('05:00');
    expect(container.querySelector('.recharts-tooltip-label')).toHaveTextContent('01.01 05:00');
  });

  it('pokazyvaet stupenchatuyu moshchnost i sutochnuyu energiyu', () => {
    sensorStatsState.context = {
      ...sensorStatsState.context,
      mode: 'equipment',
      sensorId: null,
      equipmentResourceId: 42,
      equipmentStatsScope: 'self-service',
      metric: 'power_consumption',
      chartKind: 'power',
      valueLabel: 'Потребляемая мощность',
    };
    sensorStatsState.stats = {
      ...sensorStatsState.stats,
      chartData: [{
        timestamp: '2026-01-01T00:00:00Z',
        timeMs: Date.parse('2026-01-01T00:00:00Z'),
        value: 250,
      }],
      dailyOnDurations: [{
        dateKey: '2026-01-01',
        dateLabel: '01.01',
        durationMs: 3600000,
        energyKwh: 0.35,
        partial: true,
      }],
      responseChartKind: 'power',
      chartUnit: 'W',
      energySupported: true,
    };

    const { container } = render(<SensorStatsSidebar />);

    expect(screen.getByTestId('chart-line')).toHaveAttribute('data-type', 'stepAfter');
    expect(container.querySelector('.recharts-y-axis-label')).toHaveTextContent('Мощность (Вт)');
    expect(container.querySelector('.recharts-tooltip-value')).toHaveTextContent('250.0 Вт');
    expect(screen.getByRole('region', { name: 'По дням' })).toHaveTextContent('Включено');
    expect(screen.getByRole('region', { name: 'По дням' })).toHaveTextContent('Энергия');
    expect(screen.getByRole('region', { name: 'По дням' })).toHaveTextContent('0,35 кВт·ч');
  });

  it('sohranyaet binarnyj fallback i pokazyvaet otsutstvie energii', () => {
    sensorStatsState.context = {
      ...sensorStatsState.context,
      mode: 'equipment',
      sensorId: null,
      equipmentResourceId: 43,
      equipmentStatsScope: 'self-service',
      metric: 'power_consumption',
      chartKind: 'power',
    };
    sensorStatsState.stats = {
      ...sensorStatsState.stats,
      chartData: [{
        timestamp: '2026-01-01T00:00:00Z',
        timeMs: Date.parse('2026-01-01T00:00:00Z'),
        value: 1,
        rawValue: 'ON',
      }],
      dailyOnDurations: [{
        dateKey: '2026-01-01',
        dateLabel: '01.01',
        durationMs: 60000,
        energyKwh: null,
        partial: true,
      }],
      responseChartKind: 'binary',
      chartUnit: null,
      energySupported: false,
    };

    render(<SensorStatsSidebar />);

    expect(screen.getByRole('region', { name: 'По дням' })).toHaveTextContent('Энергия');
    expect(screen.getByRole('region', { name: 'По дням' })).toHaveTextContent('Нет данных');
  });
});
