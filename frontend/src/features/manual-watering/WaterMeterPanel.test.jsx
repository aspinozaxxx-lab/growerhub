import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchWaterMeterStatistics } from '../../api/selfService';
import WaterMeterPanel from './WaterMeterPanel';

vi.mock('../../api/selfService', () => ({ fetchWaterMeterStatistics: vi.fn() }));
const device = { coordinator_id: 'my-coordinator', ieee_address: '0x1', friendly_name: 'Кран грядки' };
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('WaterMeterPanel', () => {
  it('pokazyvaet tolko servernyj obem i ne vydajot neizvestnyj den za nol', async () => {
    fetchWaterMeterStatistics.mockResolvedValue({ supported: true, month: '2026-10', known_volume_l: 42,
      days: [{ date: '2026-10-01', known_volume_l: 42, operation_count: 1 },
        { date: '2026-10-02', known_volume_l: null, operation_count: 0 }], operations: [] });
    render(<WaterMeterPanel device={device} onClose={() => {}} />);
    expect(await screen.findByText('Месяц по отчётам')).toBeInTheDocument();
    expect(screen.getByText('Нет данных')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Включить' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Месяц'), { target: { value: '2026-09' } });
    await waitFor(() => expect(fetchWaterMeterStatistics).toHaveBeenLastCalledWith('my-coordinator', '0x1', '2026-09'));
  });

  it('ne podmenyaet dannye pri osibke chteniya i daet povtor', async () => {
    fetchWaterMeterStatistics.mockRejectedValueOnce(new Error('Нет связи'))
      .mockResolvedValueOnce({ supported: true, month: '2026-10', days: [], operations: [], known_volume_l: null });
    render(<WaterMeterPanel device={device} onClose={() => {}} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Нет связи');
    fireEvent.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('Месяц по отчётам')).toBeInTheDocument();
  });
});
