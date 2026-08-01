import React from 'react';
import { cleanup, render } from '@testing-library/react';
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

  function Tooltip({ content, labelFormatter }) {
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

    return ReactModule.createElement(
      'p',
      { className: 'recharts-tooltip-label' },
      labelFormatter(fallbackLabel, payload),
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

  const Empty = () => null;
  const ResponsiveContainer = ({ children }) => children;

  return {
    Bar: Empty,
    BarChart: Chart,
    CartesianGrid: Empty,
    Line: Empty,
    LineChart: Chart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis: Empty,
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
});
