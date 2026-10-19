import React from 'react';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, test, vi } from 'vitest';
import AppPlants from './AppPlants';
import { fetchPlants } from '../../api/plants';
import { careRequest } from '../../api/care';

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../../features/sensors/SensorStatsContext', () => ({ useSensorStatsContext: () => ({ openSensorStats: vi.fn() }) }));
vi.mock('../../features/watering/WateringSidebarContext', () => ({ useWateringSidebar: () => ({ refreshVersion: 0 }) }));
vi.mock('../../features/watering/usePumpWateringStatus', () => ({ default: () => ({ isRunning: false }) }));
vi.mock('../../api/plants', () => ({ fetchPlants: vi.fn(), harvestPlant: vi.fn() }));
vi.mock('../../api/care', () => ({ careRequest: vi.fn() }));
vi.mock('../../api/selfService', () => ({ fetchFarmsOverview: async () => ({ farms: [] }) }));
vi.mock('../../components/plants/PlantEditDialog', () => ({ default: () => null }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

test('rastenie ot pomoshchnika i rastenie s oborudovaniem ispolzuyut odnu kartochku', async () => {
  fetchPlants.mockResolvedValue([
    { id: 1, name: 'Первый розмарин', avatar_key: 'rosemary', avatar_stage: 'peak', pumps: [{ id: 3, channel: 1 }] },
    { id: 2, name: 'Розмарин от помощника', avatar_key: 'rosemary', avatar_stage: 'young' },
  ]);
  careRequest.mockImplementation(async path => path === '/summaries'
    ? [{ plantId: 2, lastEntryAt: '2026-10-19T08:00:00Z' }]
    : [{ plantId: 2, title: 'Проверить листья', dueAt: '2026-10-20T08:00:00Z' }]);
  render(<MemoryRouter><AppPlants /></MemoryRouter>);
  const first = (await screen.findByRole('heading', { name: 'Первый розмарин' })).closest('.plant-card');
  const assistant = screen.getByRole('heading', { name: 'Розмарин от помощника' }).closest('.plant-card');
  expect(first).toBeTruthy(); expect(assistant).toBeTruthy();
  expect(within(first).getByText('Насосы')).toBeInTheDocument();
  expect(within(assistant).queryByText('Насосы')).not.toBeInTheDocument();
  expect(screen.queryByText('Нет датчиков')).not.toBeInTheDocument();
  expect(within(assistant).getByText(/Последняя запись/)).toBeInTheDocument();
  expect(within(assistant).getByText(/Проверить листья/)).toBeInTheDocument();
  expect(assistant.querySelector('img').getAttribute('src')).toContain('/rosemary/young-192.webp');
  fireEvent.click(within(assistant).getByRole('button', { name: 'Журнал' }));
});
