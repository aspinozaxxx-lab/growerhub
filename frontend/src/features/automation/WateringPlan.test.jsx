import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { fetchWateringPlan } from '../../api/selfService';
import WateringPlan from './WateringPlan';
import WateringScenarioFields from './WateringScenarioFields';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/selfService', () => ({ fetchWateringPlan: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it('pokazyvaet prichiny servera i ne sozdayot sobstvennoe reshenie poliva', async () => {
  fetchWateringPlan.mockResolvedValue({ observe_only: true, planned_at: null, run_seconds: 30,
    used_today_seconds: 20, last_watered_at: null, reasons: ['Нет актуальных данных'], soil: { current: 35, response_verified: false } });
  render(<WateringPlan greenhouseId={7} revision="1" />);
  expect(await screen.findByText('План · наблюдение')).toBeInTheDocument();
  fireEvent.click(screen.getByText('План · наблюдение'));
  expect(screen.getByText('Нет актуальных данных')).toBeInTheDocument();
  expect(fetchWateringPlan).toHaveBeenCalledWith(7);
  expect(screen.queryByRole('button', { name: 'Полить' })).not.toBeInTheDocument();
});

it('vybor raspisaniya nachinaetsya s nablyudeniya i ne vklyuchaet scenarij', () => {
  const onChange = vi.fn();
  render(<WateringScenarioFields config={{ run_seconds: 30, min_interval_hours: 6, daily_max_seconds: 100 }} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText('Когда поливать'), { target: { value: 'schedule' } });
  expect(onChange).toHaveBeenCalledWith({ trigger_mode: 'schedule', observe_only: true, stop_mode: 'fixed_duration' });
  expect(onChange.mock.calls[0][0]).not.toHaveProperty('enabled');
});

it('posle sohraneniya nastroyek obnovlyaet plan i ne pokazyvaet starye prichiny', async () => {
  fetchWateringPlan.mockResolvedValueOnce({ observe_only: true, reasons: ['Прежний план'], run_seconds: 30 });
  const view = render(<WateringPlan greenhouseId={7} revision="1" />);
  await screen.findByText('Прежний план');
  fetchWateringPlan.mockResolvedValueOnce({ observe_only: true, reasons: ['Новый план'], run_seconds: 40 });
  view.rerender(<WateringPlan greenhouseId={7} revision="2" />);
  expect(screen.queryByText('Прежний план')).not.toBeInTheDocument();
  await screen.findByText('Новый план');
  await waitFor(() => expect(fetchWateringPlan).toHaveBeenCalledTimes(2));
});

it('raspisanie ne trebuet polya absolyutnogo procenta pochvy', () => {
  render(<WateringScenarioFields config={{ trigger_mode: 'schedule', observe_only: true, run_seconds: 30, min_interval_hours: 6,
    daily_max_seconds: 100, schedule_days: [1, 3, 5] }} onChange={() => {}} />);
  expect(screen.queryByLabelText('Порог почвы, %')).not.toBeInTheDocument();
  expect(screen.getByLabelText('Время полива')).toBeInTheDocument();
  const days = within(screen.getByRole('group', { name: 'Дни полива' })).getAllByRole('checkbox');
  expect(days[0]).toBeChecked();
  expect(days[1]).not.toBeChecked();
});

it('pokazyvaet neizvestnuyu veroyatnost i predel perenosa bez sobstvennogo prognoza', async () => {
  fetchWateringPlan.mockResolvedValue({ observe_only: true, reasons: ['Ожидается дождь'], run_seconds: 30,
    weather: { source: 'SIMULATED', status: 'postponed', expected_mm: 6, max_period_probability: null,
      pending: { originalAt: '2026-10-03T07:00:00', deadline: '2026-10-04T07:00:00' } } });
  render(<WateringPlan greenhouseId={7} revision="1" />);
  expect(await screen.findByText('Ожидание погоды')).toBeInTheDocument();
  expect(screen.getByText('Не предоставлена')).toBeInTheDocument();
  expect(screen.getByText(/Предел ожидания/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Обновить план' }));
  await waitFor(() => expect(fetchWateringPlan).toHaveBeenCalledTimes(2));
});

it('uchet dozhdya ne vklyuchaet upravlenie i ne trebuet absolyutnogo procenta pochvy', () => {
  const onChange = vi.fn();
  render(<MemoryRouter><WateringScenarioFields config={{ trigger_mode: 'schedule', observe_only: true, weather_enabled: true,
    rain_exposure: 'outdoors', rain_threshold_mm: 2, rain_max_delay_hours: 24, run_seconds: 30 }} onChange={onChange} /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Если прогноз недоступен'), { target: { value: 'base' } });
  expect(onChange).toHaveBeenCalledWith({ weather_unavailable_policy: 'base' });
  expect(onChange.mock.calls[0][0]).not.toHaveProperty('enabled');
  expect(onChange.mock.calls[0][0]).not.toHaveProperty('observe_only');
});
