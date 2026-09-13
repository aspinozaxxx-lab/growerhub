import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import AppDemoTools from './AppDemoTools';
import { changeDemoEnvironment } from '../../api/demo';

vi.mock('../../features/auth/AuthContext', () => ({ useAuth: () => ({ demoActive: true }) }));
vi.mock('../../api/demo', () => ({
  fetchDemoStatus: vi.fn().mockResolvedValue({ devices: [{ id: 'sensor', name: 'Рассада', profile: 'controller', state: { temperature: 23.43, moisture: 81.5 } }] }),
  fetchDemoCatalog: vi.fn().mockResolvedValue([{ key: 'controller', name: 'Контроллер' }]),
  changeDemoEnvironment: vi.fn().mockResolvedValue({ devices: [{ id: 'sensor', name: 'Рассада', profile: 'controller', state: { temperature: 34, moisture: 81.5 } }] }),
  addDemoDevice: vi.fn(),
}));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('prinimaet drobnye pokazaniya posle simuljacii i menjaet tolko vybrannye uslovija', async () => {
  render(<MemoryRouter><AppDemoTools /></MemoryRouter>);
  const temperature = await screen.findByLabelText('Температура воздуха, °C');
  await waitFor(() => {
    expect(temperature).toHaveValue(23.43);
    expect(screen.getByLabelText('Влажность почвы, %')).toHaveValue(81.5);
  });
  expect(temperature.checkValidity()).toBe(true);
  expect(screen.getByLabelText('Влажность почвы, %').checkValidity()).toBe(true);
  fireEvent.change(temperature, { target: { value: '34' } });
  fireEvent.click(screen.getByRole('button', { name: 'Применить условия' }));
  await waitFor(() => expect(changeDemoEnvironment).toHaveBeenCalledWith({ device_id: 'sensor', temperature: 34, moisture: 81.5 }));
  expect(await screen.findByRole('status')).toHaveTextContent('Показания изменены');
});
