import React from 'react';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createGreenhouse,
  createUserFarm,
  deleteGreenhouse,
  deleteUserFarm,
  fetchFarmsOverview,
  updateGreenhouse,
  updateUserFarm,
  updateFarmWeatherLocation,
} from '../../api/selfService';
import FarmZonesSettings from './FarmZonesSettings';

vi.mock('../../api/selfService', () => ({
  createGreenhouse: vi.fn(),
  createUserFarm: vi.fn(),
  deleteGreenhouse: vi.fn(),
  deleteUserFarm: vi.fn(),
  fetchFarmsOverview: vi.fn(),
  updateGreenhouse: vi.fn(),
  updateUserFarm: vi.fn(),
  updateFarmWeatherLocation: vi.fn(),
}));

const overview = {
  farms: [
    {
      id: 1,
      name: 'Основное помещение',
      enabled: true,
      greenhouses: [{
        id: 11,
        farm_id: 1,
        name: 'Северная теплица',
        enabled: true,
      }],
    },
    {
      id: 2,
      name: 'Второе помещение',
      enabled: true,
      greenhouses: [],
    },
  ],
};

describe('FarmZonesSettings', () => {
  beforeEach(() => {
    fetchFarmsOverview.mockResolvedValue(overview);
    updateGreenhouse.mockResolvedValue({
      farms: [
        { ...overview.farms[0], greenhouses: [] },
        { ...overview.farms[1], greenhouses: [{
          ...overview.farms[0].greenhouses[0],
          farm_id: 2,
        }] },
      ],
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('sohranyaet mesto tolko vybrannoj fermy s okruglennymi koordinatami bez avtomaticheskogo GPS', async () => {
    updateFarmWeatherLocation.mockResolvedValue(overview);
    render(<FarmZonesSettings />);
    const farm = (await screen.findAllByLabelText('Название фермы'))[0].closest('section');
    const view = within(farm);
    fireEvent.click(view.getByText('Место для прогноза'));
    fireEvent.change(view.getByLabelText('Широта'), { target: { value: '51.50741' } });
    fireEvent.change(view.getByLabelText('Долгота'), { target: { value: '-0.12789' } });
    fireEvent.click(view.getByRole('button', { name: 'Сохранить место' }));
    await waitFor(() => expect(updateFarmWeatherLocation).toHaveBeenCalledWith(1, { latitude: 51.51, longitude: -0.13, label: null }));
    expect(updateUserFarm).not.toHaveBeenCalled();
  });

  it('upravlyaet imenami i perenosom teplic otdelno ot konstruktora', async () => {
    render(<FarmZonesSettings />);

    const greenhouseName = await screen.findByDisplayValue('Северная теплица');
    const greenhouse = greenhouseName.closest('article');
    expect(greenhouse).not.toBeNull();
    fireEvent.click(greenhouse.querySelector('summary'));
    expect(screen.getByText(
      'Ферма — это помещение. В каждой ферме можно создать несколько теплиц и переносить их между помещениями.',
    )).toBeInTheDocument();

    fireEvent.change(within(greenhouse).getByLabelText('Ферма'), {
      target: { value: '2' },
    });
    fireEvent.click(within(greenhouse).getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => {
      expect(updateGreenhouse).toHaveBeenCalledWith(11, {
        farm_id: 2,
        name: 'Северная теплица',
        enabled: true,
      });
    });
    expect(createGreenhouse).not.toHaveBeenCalled();
    expect(createUserFarm).not.toHaveBeenCalled();
    expect(deleteGreenhouse).not.toHaveBeenCalled();
    expect(deleteUserFarm).not.toHaveBeenCalled();
    expect(updateUserFarm).not.toHaveBeenCalled();
  });
});
