import React from 'react';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { updatePlant } from '../../api/plants';
import PlantEditDialog from './PlantEditDialog';

const pendingEffects = vi.hoisted(() => []);

vi.mock('react', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useEffect: (effect, dependencies) => actual.useEffect(() => {
      // Translitem: Otdelyaem pozdnie passivnye effekty ot dostupa k uzhe otkrytoj forme.
      let active = true;
      let dispose;
      pendingEffects.push(() => { if (active) dispose = effect(); });
      return () => { active = false; dispose?.(); };
    }, dependencies),
  };
});

vi.mock('../../features/auth/AuthContext', () => ({
  useAuth: () => ({ token: 'test-token' }),
}));

vi.mock('../../api/plants', () => ({
  createPlant: vi.fn(),
  updatePlant: vi.fn(),
  deletePlant: vi.fn(),
}));

const plant = { id: 5, name: 'Томат', plant_type: 'flowering_plants', zone: { id: 2 } };
const zones = [{ id: 2, name: 'Северная' }, { id: 3, name: 'Южная' }];
const flushPassiveEffects = () => act(() => {
  while (pendingEffects.length) pendingEffects.shift()();
});

afterEach(() => {
  cleanup();
  pendingEffects.length = 0;
  vi.clearAllMocks();
});

describe('PlantEditDialog', () => {
  it('ne zamenyaet vybrannuyu teplicu pozdnej inicializaciej formy', async () => {
    updatePlant.mockResolvedValue({});
    render(<PlantEditDialog isOpen mode="edit" plant={plant} zones={zones} />);

    fireEvent.change(screen.getByLabelText('Теплица'), { target: { value: '3' } });
    flushPassiveEffects();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Сохранить' })));

    expect(updatePlant).toHaveBeenCalledWith('test-token', 5, expect.objectContaining({ zone_id: 3 }));
  });

  it('sbros chernovika pri povtornom otkrytii ne zhdet passivnyh effektov', () => {
    const { rerender } = render(<PlantEditDialog isOpen mode="edit" plant={plant} zones={zones} />);
    flushPassiveEffects();
    fireEvent.change(screen.getByLabelText('Название'), { target: { value: 'Черновик' } });
    fireEvent.change(screen.getByLabelText('Теплица'), { target: { value: '3' } });

    rerender(<PlantEditDialog isOpen={false} mode="edit" plant={plant} zones={zones} />);
    flushPassiveEffects();
    rerender(<PlantEditDialog isOpen mode="edit" plant={plant} zones={zones} />);

    expect(screen.getByLabelText('Название')).toHaveValue('Томат');
    expect(screen.getByLabelText('Теплица')).toHaveValue('2');
  });

  it('vybiraet lyubuyu kartinku i imennuyu stadiyu ne menyaya stadiyu vyrashchivaniya', async () => {
    updatePlant.mockResolvedValue({});
    render(<PlantEditDialog isOpen mode="edit" plant={{ ...plant, growth_stage: 'vegetative', avatar_key: 'tomato', avatar_stage: 'young' }} zones={zones} />);
    fireEvent.click(screen.getByText('Картинка растения').closest('summary'));
    expect(within(screen.getByRole('radiogroup', { name: 'Растение на картинке' })).getAllByRole('radio')).toHaveLength(11);
    expect(screen.getByRole('radio', { name: 'Молодой куст', exact: true })).toBeChecked();
    fireEvent.click(screen.getByRole('radio', { name: 'Петуния', exact: true }));
    fireEvent.click(screen.getByRole('radio', { name: 'Бутоны', exact: true }));
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Сохранить' })));
    expect(updatePlant).toHaveBeenCalledWith('test-token', 5, expect.objectContaining({ avatar_key: 'petunia', avatar_stage: 'mature', growth_stage: 'vegetative' }));
  });

  it('vozvrashchaet avtomaticheskuyu kartinku i sbros stadii bez poteri ostalnyh polej', async () => {
    updatePlant.mockResolvedValue({});
    render(<PlantEditDialog isOpen mode="edit" plant={{ ...plant, avatar_key: 'basil', avatar_stage: 'young' }} zones={zones} />);
    fireEvent.click(screen.getByText('Картинка растения').closest('summary'));
    fireEvent.click(screen.getByRole('radio', { name: 'Автоматически', exact: true }));
    expect(screen.queryByRole('group', { name: 'Стадия на картинке' })).not.toBeInTheDocument();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Сохранить' })));
    expect(updatePlant).toHaveBeenCalledWith('test-token', 5, expect.objectContaining({ avatar_key: '', avatar_stage: '', name: 'Томат', zone_id: 2 }));
  });
});
